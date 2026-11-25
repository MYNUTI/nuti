package com.example.nutriuniv.domain.coupang.client;

import com.example.nutriuniv.domain.coupang.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.TimeZone;

@Slf4j
@Component
public class CoupangApiClient {

    @Value("${coupang.access-key}")
    private String accessKey;

    @Value("${coupang.secret-key}")
    private String secretKey;

    private static final String BASE_URL    = "https://api-gateway.coupang.com";
    private static final String SEARCH_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";
    private static final String REPORT_PATH = "/v2/providers/affiliate_open_api/apis/openapi/v1/reports";

    private final RestTemplate restTemplate = new RestTemplate();

    // ── HMAC 서명 ─────────────────────────────────────────────────────────────────

    private HttpHeaders createHeaders(String method, String path, String queryString) {
        try {
            String trimmedSecret = secretKey.trim();
            String trimmedAccess = accessKey.trim();

            SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd'T'HHmmss'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String datetime = sdf.format(new Date());
            String message = datetime + method + path + queryString;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(trimmedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization",
                    "CEA algorithm=HmacSHA256, access-key=" + trimmedAccess +
                    ", signed-date=" + datetime + ", signature=" + signature);

            log.info("[Coupang] datetime={}", datetime);
            log.info("[Coupang] message=\n{}", message);
            log.info("[Coupang] Authorization: {}", headers.getFirst("Authorization"));

            return headers;
        } catch (Exception e) {
            throw new RuntimeException("HMAC 서명 생성 실패", e);
        }
    }

    // ── 상품 검색 ─────────────────────────────────────────────────────────────────

    /**
     * 가격 새로 받기용 검색 — API 오류(응답 없음 · rCode 가 0 아님)는 예외로, 검색 0건은 빈 목록으로 돌려준다.
     * 오류를 「검색되지 않음」으로 세면 쿠팡이 잠깐 안 될 때 판매 상태를 확인 못 함으로 잘못 넘어간다(DATA_006 — API 실패는 직전 값 유지).
     */
    public CoupangSearchResponse.SearchData searchOrEmpty(String keyword) {
        String queryString = "keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=10";
        URI uri = URI.create(BASE_URL + SEARCH_PATH + "?" + queryString);
        ResponseEntity<CoupangSearchResponse> response = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(createHeaders("GET", SEARCH_PATH, queryString)), CoupangSearchResponse.class);
        CoupangSearchResponse body = response.getBody();
        if (body == null || !"0".equals(body.getRCode())) {
            throw new IllegalStateException("쿠팡 검색 API 오류 - rCode=" + (body == null ? null : body.getRCode()));
        }
        CoupangSearchResponse.SearchData data = body.getData() == null ? new CoupangSearchResponse.SearchData() : body.getData();
        if (data.getProductData() == null) data.setProductData(java.util.List.of());
        return data;
    }

    public CoupangSearchResponse.SearchData searchProduct(String keyword) {
        String queryString = "keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=10";
        URI uri = URI.create(BASE_URL + SEARCH_PATH + "?" + queryString);

        ResponseEntity<CoupangSearchResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders("GET", SEARCH_PATH, queryString)),
                CoupangSearchResponse.class
        );

        CoupangSearchResponse body = response.getBody();

        if (body == null) {
            log.warn("[Coupang] ❌ 응답 바디 null - keyword: {}", keyword);
            return null;
        }

        // 항상 rCode + rMessage 출력
        log.info("[Coupang] rCode={}, rMessage={}, keyword={}", body.getRCode(), body.getRMessage(), keyword);

        if (!"0".equals(body.getRCode())) {
            log.warn("[Coupang] ❌ API 오류 - rCode={}, rMessage={}, keyword={}",
                    body.getRCode(), body.getRMessage(), keyword);
            return null;
        }

        if (body.getData() == null || body.getData().getProductData() == null) {
            log.warn("[Coupang] ⚠️ 검색 결과 data 없음 - keyword={}", keyword);
            return null;
        }

        if (body.getData().getProductData().isEmpty()) {
            log.warn("[Coupang] ⚠️ 검색 결과 0건 - keyword={}", keyword);
            return null;
        }

        log.info("[Coupang] ✅ 검색 성공 - {}건 반환, keyword={}", body.getData().getProductData().size(), keyword);
        return body.getData();
    }

    // ── 리포트 조회 ───────────────────────────────────────────────────────────────

    public List<CoupangClickData> getClickReport(String startDate, String endDate) {
        String path = REPORT_PATH + "/clicks";
        String queryString = "startDate=" + startDate + "&endDate=" + endDate;
        ResponseEntity<CoupangClickResponse> response = restTemplate.exchange(
                BASE_URL + path + "?" + queryString,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders("GET", path, queryString)),
                CoupangClickResponse.class
        );
        CoupangClickResponse body = response.getBody();
        return body != null && body.getData() != null ? body.getData() : List.of();
    }

    public List<CoupangOrderData> getOrderReport(String startDate, String endDate) {
        String path = REPORT_PATH + "/orders";
        String queryString = "startDate=" + startDate + "&endDate=" + endDate;
        ResponseEntity<CoupangOrderResponse> response = restTemplate.exchange(
                BASE_URL + path + "?" + queryString,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders("GET", path, queryString)),
                CoupangOrderResponse.class
        );
        CoupangOrderResponse body = response.getBody();
        return body != null && body.getData() != null ? body.getData() : List.of();
    }

    public List<CoupangCancelData> getCancelReport(String startDate, String endDate) {
        String path = REPORT_PATH + "/cancels";
        String queryString = "startDate=" + startDate + "&endDate=" + endDate;
        ResponseEntity<CoupangCancelResponse> response = restTemplate.exchange(
                BASE_URL + path + "?" + queryString,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders("GET", path, queryString)),
                CoupangCancelResponse.class
        );
        CoupangCancelResponse body = response.getBody();
        return body != null && body.getData() != null ? body.getData() : List.of();
    }

    public List<CoupangCommissionData> getCommissionReport(String startDate, String endDate) {
        String path = REPORT_PATH + "/commission";
        String queryString = "startDate=" + startDate + "&endDate=" + endDate;
        ResponseEntity<CoupangCommissionResponse> response = restTemplate.exchange(
                BASE_URL + path + "?" + queryString,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders("GET", path, queryString)),
                CoupangCommissionResponse.class
        );
        CoupangCommissionResponse body = response.getBody();
        return body != null && body.getData() != null ? body.getData() : List.of();
    }
}
