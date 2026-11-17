package com.example.nutriuniv.domain.publicdata.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공공데이터포털 (sync.md 「입력」).
 * <pre>https://apis.data.go.kr/{기관}/{서비스}/{오퍼레이션}?serviceKey={키}&pageNo=1&numOfRows=100&type=json</pre>
 * <ul>
 *   <li>키는 Decoding 키를 받아 여기서 한 번만 URL 인코딩한다(Encoding 키를 넣으면 두 번 인코딩돼 실패)</li>
 *   <li>응답 모양이 서비스마다 조금씩 다르다 — {header, body} 또는 {response:{header, body}}, items 가 배열 · {item:[…]} · {item:{…}}.
 *       어느 모양이든 읽는다</li>
 *   <li>게이트웨이 오류(한도 초과 · 키 미등록)는 type=json 이어도 XML 로 온다 — returnReasonCode 로 판정</li>
 * </ul>
 * 서비스마다 실제 응답 모양은 키를 받은 뒤 한 번 호출해 확인해야 한다(특히 HACCP B553748 는 오퍼레이션 이름 · 파라미터 확인 필요).
 */
@Slf4j
@Component
public class DataGoKrClient implements PublicApiClient {

    static final String BASE = "https://apis.data.go.kr/";

    static final Set<String> QUOTA = Set.of("22");
    static final Set<String> KEY = Set.of("20", "30", "31", "32");
    static final Set<String> RETRY = Set.of("01", "02", "04", "05");
    private static final Pattern XML_CODE = Pattern.compile("<(returnReasonCode|resultCode)>\\s*([^<\\s]+)\\s*</");
    private static final Pattern XML_MSG = Pattern.compile("<(returnAuthMsg|resultMsg|errMsg)>\\s*([^<]*)</");

    private final PublicApiHttp http;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    public DataGoKrClient(PublicApiHttp http, ObjectMapper objectMapper,
                          @Value("${publicdata.datagokr.service-key:}") String serviceKey) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    @Override
    public PublicApi.Provider provider() {
        return PublicApi.Provider.DATA_GO_KR;
    }

    @Override
    public ApiPage fetch(PublicApi api, int page, String conditionValue) {
        if (serviceKey.isEmpty()) return ApiPage.of(ApiPage.Status.KEY_REJECTED, "NO_KEY", "publicdata.datagokr.service-key 가 설정되지 않았습니다");
        String url = BASE + api.path()
                + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&pageNo=" + page
                + "&numOfRows=" + api.pageSize()
                + "&type=json";
        try {
            PublicApiHttp.Result res = http.get(URI.create(url));
            if (res.serverError() && !res.body().strip().startsWith("<")) {
                return ApiPage.of(ApiPage.Status.RETRY_LATER, "HTTP_" + res.status(), "서버 오류");
            }
            return parse(res.body(), objectMapper);
        } catch (Exception e) {
            log.warn("[COLLECT] {} page={} 호출 실패 — {}", api, page, e.getMessage());
            return ApiPage.of(ApiPage.Status.RETRY_LATER, "NETWORK", e.getMessage());
        }
    }

    /** 응답 본문 → ApiPage. 순수 함수(테스트용으로 열어 둠). */
    @SuppressWarnings("unchecked")
    static ApiPage parse(String body, ObjectMapper mapper) {
        String b = body == null ? "" : body.strip();
        if (b.isEmpty()) return ApiPage.of(ApiPage.Status.RETRY_LATER, "EMPTY", "빈 응답");
        if (b.contains("alert(")) return ApiPage.of(ApiPage.Status.KEY_REJECTED, "HTML", "HTML 응답 — 인증키 확인 필요");
        if (b.startsWith("<")) return parseXml(b);

        Map<String, Object> root;
        try {
            root = mapper.readValue(b, Map.class);
        } catch (Exception e) {
            return ApiPage.of(ApiPage.Status.ERROR, "PARSE", "JSON 을 읽을 수 없습니다: " + e.getMessage());
        }
        Map<String, Object> top = root.get("response") instanceof Map ? (Map<String, Object>) root.get("response") : root;
        Map<String, Object> header = top.get("header") instanceof Map ? (Map<String, Object>) top.get("header") : Map.of();
        Map<String, Object> bodyNode = top.get("body") instanceof Map ? (Map<String, Object>) top.get("body") : Map.of();
        String code = String.valueOf(header.getOrDefault("resultCode", ""));
        String msg = String.valueOf(header.getOrDefault("resultMsg", ""));

        if (code.equals("00")) {
            List<Map<String, Object>> rows = items(bodyNode.get("items"));
            return new ApiPage(ApiPage.Status.OK, code, msg, FoodSafetyClient.toLong(bodyNode.get("totalCount")), rows);
        }
        if (code.equals("03")) return new ApiPage(ApiPage.Status.NO_DATA, code, msg, 0L, List.of());
        return classify(code, msg);
    }

    /** items 가 배열 · {item:[…]} · {item:{…}} · 배열 안에 {item:{…}} 어느 모양이든 행 목록으로. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> items(Object items) {
        if (items == null) return List.of();
        if (items instanceof Map<?, ?> m) {
            Object item = m.get("item");
            if (item == null) return List.of();
            return items(item instanceof List ? item : List.of(item));
        }
        if (items instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> row = (Map<String, Object>) o;
                if (row.size() == 1 && row.get("item") instanceof Map) row = (Map<String, Object>) row.get("item");
                out.add(row);
            }
            return out;
        }
        return List.of();
    }

    private static ApiPage parseXml(String b) {
        Matcher c = XML_CODE.matcher(b);
        Matcher m = XML_MSG.matcher(b);
        String code = c.find() ? c.group(2) : "XML";
        String msg = m.find() ? m.group(2).strip() : "XML 응답";
        if (code.equals("00")) return ApiPage.of(ApiPage.Status.ERROR, code, "type=json 인데 XML 로 정상 응답 — 파라미터 확인 필요");
        if (code.equals("03")) return new ApiPage(ApiPage.Status.NO_DATA, code, msg, 0L, List.of());
        return classify(code, msg);
    }

    private static ApiPage classify(String code, String msg) {
        if (QUOTA.contains(code)) return ApiPage.of(ApiPage.Status.QUOTA_EXCEEDED, code, msg);
        if (KEY.contains(code)) return ApiPage.of(ApiPage.Status.KEY_REJECTED, code, msg);
        if (RETRY.contains(code)) return ApiPage.of(ApiPage.Status.RETRY_LATER, code, msg);
        return ApiPage.of(ApiPage.Status.ERROR, code, msg);
    }
}