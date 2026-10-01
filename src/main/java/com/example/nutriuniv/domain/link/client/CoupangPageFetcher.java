package com.example.nutriuniv.domain.link.client;

import com.example.nutriuniv.domain.link.util.LinkUrlNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 쿠팡 상품 페이지 조회 (링크 인식) — 전체 3초 예산, 리다이렉트는 직접 따라가며 매 단계 호스트를 확인한다(쿠팡 호스트 밖으로는 나가지 않는다).
 * 본문은 앞 512KB 만 읽는다(제목은 head 에 있다). 실패·차단·시간 초과는 Optional.empty — 호출자는 matched=null 로 정상 응답한다.
 */
@Slf4j
@Component
public class CoupangPageFetcher {

    public static final Duration BUDGET = Duration.ofSeconds(3);
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 512 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 NutriUnivLinkResolver/1.0";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(BUDGET)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record Page(URI finalUri, String html) {}

    public Optional<Page> fetch(URI start) {
        long deadline = System.nanoTime() + BUDGET.toNanos();
        URI current = start;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!LinkUrlNormalizer.isCoupangHost(current.getHost())) {
                    log.info("[LINK] 쿠팡 밖으로 리다이렉트 — 중단: {}", current.getHost());
                    return Optional.empty();
                }
                long remainingMs = Math.max(300, (deadline - System.nanoTime()) / 1_000_000);
                HttpRequest req = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofMillis(remainingMs))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "ko-KR,ko;q=0.9")
                        .GET()
                        .build();
                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int status = res.statusCode();
                if (status >= 300 && status < 400) {
                    Optional<String> location = res.headers().firstValue("Location");
                    try (InputStream ignored = res.body()) { /* 본문 버림 */ }
                    if (location.isEmpty()) return Optional.empty();
                    current = current.resolve(location.get());
                    continue;
                }
                try (InputStream in = res.body()) {
                    if (status != 200) {
                        log.info("[LINK] 쿠팡 페이지 응답 {} — {}", status, current);
                        return Optional.empty();
                    }
                    byte[] bytes = in.readNBytes(MAX_BYTES);
                    Charset cs = charsetOf(res.headers().firstValue("Content-Type").orElse(null));
                    return Optional.of(new Page(current, new String(bytes, cs)));
                }
            }
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            log.info("[LINK] 쿠팡 페이지 조회 실패 — {} ({})", start, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Charset charsetOf(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        int i = contentType.toLowerCase().indexOf("charset=");
        if (i < 0) return StandardCharsets.UTF_8;
        String name = contentType.substring(i + 8).split("[;\\s]")[0].replace("\"", "").trim();
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
