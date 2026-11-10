package com.example.nutriuniv.domain.event.dto;

import java.util.List;
import java.util.Map;

/** POST /events 입력 — 한 번에 최대 100개. occurredAt 은 ISO-8601(시간대 있으면 KST 로 바꿈), 없거나 틀리면 받은 시각. */
public record EventBatchRequest(List<Event> events) {

    public record Event(String name, String sessionId, String screen, String occurredAt, Map<String, Object> props) {}
}
