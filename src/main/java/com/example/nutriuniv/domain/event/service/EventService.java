package com.example.nutriuniv.domain.event.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.event.dto.EventBatchRequest;
import com.example.nutriuniv.domain.event.dto.EventBatchResponse;
import com.example.nutriuniv.domain.event.entity.EventNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 앱 이벤트 받기 (v3). 한 번에 최대 100개, 목록에 없는 이름은 버린다(오류 아님).
 * 기기 ID · 계정은 요청 헤더 · 토큰에서, 세션은 이벤트 값 → X-Session-Id 순. props 에서 몸 정보 키는 지우고 4KB 를 넘으면 버린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    public static final int MAX_BATCH = 100;
    static final int MAX_PROPS_BYTES = 4_096;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** props 에 남기지 않는 몸 정보 키 — 소문자로 바꾸고 _ · - 를 뺀 뒤 그대로 같으면 지운다(page · image 같은 키는 남는다). */
    static final Set<String> BODY_KEYS = Set.of(
            "height", "heightcm", "weight", "weightkg", "age", "gender", "sex",
            "birth", "birthyear", "birthdate", "birthday", "dailykcal");

    private static final String INSERT_SQL = """
            INSERT INTO app_events (name, anonymous_id, user_id, session_id, screen, occurred_at, received_at, props)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EventBatchResponse collect(EventBatchRequest request, Actor actor) {
        List<EventBatchRequest.Event> events = request == null || request.events() == null ? List.of() : request.events();
        if (events.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "events가 비어 있습니다.");
        }
        if (events.size() > MAX_BATCH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "events는 한 번에 " + MAX_BATCH + "개까지 보낼 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Object[]> rows = new ArrayList<>();
        int dropped = 0;
        for (EventBatchRequest.Event e : events) {
            String name = e == null || e.name() == null ? null : e.name().trim().toLowerCase();
            if (name == null || !EventNames.KNOWN.contains(name)) {
                dropped++;
                continue;
            }
            String sessionId = cut(e.sessionId() != null && !e.sessionId().isBlank() ? e.sessionId() : actor.sessionId(), 36);
            rows.add(new Object[]{
                    name, actor.anonymousId(), actor.userId(), sessionId, cut(e.screen(), 50),
                    Timestamp.valueOf(parseTime(e.occurredAt(), now)), Timestamp.valueOf(now), propsJson(e.props())
            });
        }
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, rows);
        }
        return new EventBatchResponse(rows.size(), dropped);
    }

    /** ISO-8601 — 시간대가 있으면 KST 로, 없으면 그대로. 없거나 틀리거나 받은 시각보다 하루 넘게 뒤면 받은 시각. */
    static LocalDateTime parseTime(String raw, LocalDateTime now) {
        if (raw == null || raw.isBlank()) return now;
        LocalDateTime t;
        try {
            t = OffsetDateTime.parse(raw.trim()).atZoneSameInstant(KST).toLocalDateTime();
        } catch (DateTimeParseException e1) {
            try {
                t = LocalDateTime.parse(raw.trim());
            } catch (DateTimeParseException e2) {
                return now;
            }
        }
        return t.isAfter(now.plusDays(1)) ? now : t;
    }

    String propsJson(Map<String, Object> props) {
        if (props == null || props.isEmpty()) return null;
        try {
            String json = objectMapper.writeValueAsString(stripBody(props));
            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PROPS_BYTES ? null : json;
        } catch (Exception ex) {
            log.debug("[EVENT] props 직렬화 실패 — {}", ex.getMessage());
            return null;
        }
    }

    /** 몸 정보 키를 지운 사본 (안쪽 객체까지). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> stripBody(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> en : in.entrySet()) {
            String k = en.getKey() == null ? "" : en.getKey().toLowerCase().replace("_", "").replace("-", "");
            if (BODY_KEYS.contains(k)) continue;
            Object v = en.getValue();
            out.put(en.getKey(), v instanceof Map<?, ?> m ? stripBody((Map<String, Object>) m) : v);
        }
        return out;
    }

    private static String cut(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
