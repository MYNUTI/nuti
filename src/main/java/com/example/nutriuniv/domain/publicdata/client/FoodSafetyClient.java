package com.example.nutriuniv.domain.publicdata.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 식품안전나라 (sync.md 「입력」 · 「완료 확인과 오류 처리」).
 * <pre>http://openapi.foodsafetykorea.go.kr/api/{인증키}/{서비스ID}/json/{시작}/{끝}[/{조건}={값}]</pre>
 * 응답: {"C002":{"total_count":"1081400","row":[…],"RESULT":{"CODE":"INFO-000","MSG":"…"}}}.
 * 키가 틀리거나 미신청이면 HTTP 200 에 HTML(alert)이 온다. 한도 초과도 HTTP 200 에 빈 응답처럼 온다(INFO-300).
 * 조건 이름을 틀리면 오류 없이 전체 목록이 온다 — 증분 행 수가 비정상적으로 크면 수집기가 실패로 본다.
 */
@Slf4j
@Component
public class FoodSafetyClient implements PublicApiClient {

    static final String BASE = "http://openapi.foodsafetykorea.go.kr/api/";

    static final Set<String> QUOTA = Set.of("INFO-300", "ERROR-503", "INFO-320");
    static final Set<String> RETRY = Set.of("ERROR-500", "INFO-700");
    static final Set<String> KEY = Set.of("INFO-100");

    private final PublicApiHttp http;
    private final ObjectMapper objectMapper;
    private final String key;

    public FoodSafetyClient(PublicApiHttp http, ObjectMapper objectMapper,
                            @Value("${publicdata.foodsafety.key:}") String key) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.key = key == null ? "" : key.trim();
    }

    @Override
    public PublicApi.Provider provider() {
        return PublicApi.Provider.FOOD_SAFETY;
    }

    @Override
    public ApiPage fetch(PublicApi api, int page, String conditionValue) {
        if (key.isEmpty()) return ApiPage.of(ApiPage.Status.KEY_REJECTED, "NO_KEY", "publicdata.foodsafety.key 가 설정되지 않았습니다");
        int start = (page - 1) * api.pageSize() + 1;
        int end = page * api.pageSize();
        StringBuilder url = new StringBuilder(BASE).append(key).append('/').append(api.path())
                .append("/json/").append(start).append('/').append(end);
        if (conditionValue != null && api.condition().param() != null) {
            url.append('/').append(api.condition().param()).append('=').append(conditionValue);
        }
        try {
            PublicApiHttp.Result res = http.get(URI.create(url.toString()));
            if (res.serverError()) return ApiPage.of(ApiPage.Status.RETRY_LATER, "HTTP_" + res.status(), "서버 오류");
            return parse(api.path(), res.body(), objectMapper);
        } catch (Exception e) {
            log.warn("[COLLECT] {} page={} 호출 실패 — {}", api, page, e.getMessage());
            return ApiPage.of(ApiPage.Status.RETRY_LATER, "NETWORK", e.getMessage());
        }
    }

    /** 응답 본문 → ApiPage. 순수 함수(테스트용으로 열어 둠). */
    @SuppressWarnings("unchecked")
    static ApiPage parse(String serviceId, String body, ObjectMapper mapper) {
        String b = body == null ? "" : body.strip();
        if (b.isEmpty()) return ApiPage.of(ApiPage.Status.RETRY_LATER, "EMPTY", "빈 응답");
        if (b.startsWith("<") || b.contains("alert(")) {
            return ApiPage.of(ApiPage.Status.KEY_REJECTED, "HTML", "JSON 이 아닌 HTML 응답 — 인증키가 틀렸거나 미신청 API");
        }
        Map<String, Object> root;
        try {
            root = mapper.readValue(b, Map.class);
        } catch (Exception e) {
            return ApiPage.of(ApiPage.Status.ERROR, "PARSE", "JSON 을 읽을 수 없습니다: " + e.getMessage());
        }
        Object svc = root.get(serviceId);
        Map<String, Object> node = svc instanceof Map ? (Map<String, Object>) svc : root;
        Object resultObj = node.get("RESULT");
        if (!(resultObj instanceof Map)) return ApiPage.of(ApiPage.Status.ERROR, "NO_RESULT", "RESULT 가 없습니다");
        Map<String, Object> result = (Map<String, Object>) resultObj;
        String code = String.valueOf(result.getOrDefault("CODE", ""));
        String msg = String.valueOf(result.getOrDefault("MSG", ""));

        if (code.equals("INFO-000")) {
            Object rowObj = node.get("row");
            List<Map<String, Object>> rows = rowObj instanceof List ? (List<Map<String, Object>>) rowObj : List.of();
            return new ApiPage(ApiPage.Status.OK, code, msg, toLong(node.get("total_count")), rows);
        }
        if (code.equals("INFO-200")) return new ApiPage(ApiPage.Status.NO_DATA, code, msg, 0L, List.of());
        if (QUOTA.contains(code)) return ApiPage.of(ApiPage.Status.QUOTA_EXCEEDED, code, msg);
        if (RETRY.contains(code)) return ApiPage.of(ApiPage.Status.RETRY_LATER, code, msg);
        if (KEY.contains(code)) return ApiPage.of(ApiPage.Status.KEY_REJECTED, code, msg);
        return ApiPage.of(ApiPage.Status.ERROR, code, msg);
    }

    static Long toLong(Object v) {
        if (v == null) return null;
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}