package com.example.nutriuniv.domain.event.service;

import com.example.nutriuniv.domain.event.entity.EventNames;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** v3 이벤트 — 61개 이름, 몸 정보 키 제거, 시각 해석. */
class EventServiceTest {

    @Test
    void 이벤트_이름은_61개() {
        assertEquals(61, EventNames.KNOWN.size());
        assertTrue(EventNames.KNOWN.contains("scan_complete"));
        assertFalse(EventNames.KNOWN.contains("unknown_event"));
    }

    @Test
    void 몸_정보_키는_안쪽까지_지우고_다른_키는_남긴다() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("weight_kg", 60);
        inner.put("band", 2000);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("height", 170);
        props.put("birthYear", 1999);
        props.put("Gender", "FEMALE");
        props.put("page", 2);
        props.put("image", "front");
        props.put("profile", inner);

        Map<String, Object> out = EventService.stripBody(props);
        assertEquals(Map.of("page", 2, "image", "front", "profile", Map.of("band", 2000)), out);
    }

    @Test
    void 시각_해석() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0);
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 0), EventService.parseTime("2026-11-10T00:00:00Z", now));
        assertEquals(LocalDateTime.of(2026, 11, 10, 8, 30), EventService.parseTime("2026-11-10T08:30:00", now));
        assertEquals(now, EventService.parseTime("어제", now));
        assertEquals(now, EventService.parseTime(null, now));
        assertEquals(now, EventService.parseTime("2026-11-20T00:00:00", now));   // 하루 넘게 뒤
    }
}
