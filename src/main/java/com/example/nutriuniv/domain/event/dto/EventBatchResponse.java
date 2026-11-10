package com.example.nutriuniv.domain.event.dto;

/** POST /events 출력 — accepted 저장한 수, dropped 이름이 목록에 없어 버린 수. */
public record EventBatchResponse(int accepted, int dropped) {}
