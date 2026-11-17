package com.example.nutriuniv.domain.publicdata.client;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 공공 API 호출용 HTTP — 시간 제한을 꼭 건다(공용 RestTemplate 에는 시간 제한이 없어, 응답이 없으면 수집 스레드가 무한정 멈춘다).
 * <ul>
 *   <li>4xx · 5xx 도 예외로 던지지 않고 상태 코드와 본문을 돌려준다 — 판정은 클라이언트가 한다</li>
 *   <li>본문은 바이트로 받아 UTF-8 로 읽는다(Content-Type 에 charset 이 없으면 한글이 깨지는 것을 막는다)</li>
 * </ul>
 */
@Component
public class PublicApiHttp {

    public record Result(int status, String body) {
        public boolean serverError() {
            return status >= 500;
        }
    }

    private final RestClient restClient;

    public PublicApiHttp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** uri 는 이미 인코딩된 것을 그대로 쓴다(공공데이터포털 서비스키의 + · / · = 가 다시 인코딩되지 않게). 시간 초과 · 연결 실패는 예외. */
    public Result get(URI uri) {
        ResponseEntity<byte[]> res = restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })   // 예외 대신 그대로 돌려받는다
                .toEntity(byte[].class);
        byte[] body = res.getBody();
        return new Result(res.getStatusCode().value(), body == null ? "" : new String(body, StandardCharsets.UTF_8));
    }
}