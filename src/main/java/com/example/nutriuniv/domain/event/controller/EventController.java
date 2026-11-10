package com.example.nutriuniv.domain.event.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.event.dto.EventBatchRequest;
import com.example.nutriuniv.domain.event.dto.EventBatchResponse;
import com.example.nutriuniv.domain.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Event", description = "앱 이벤트 받기 (v3)")
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // POST /events
    @Operation(summary = "이벤트 묶음 보내기",
            description = "events[] 최대 100개 — name(v3 이벤트 목록 61개, 소문자) · sessionId · screen · occurredAt(ISO-8601) · props(객체). " +
                    "목록에 없는 이름은 오류 없이 버리고 dropped 로 셉니다. 기기 ID(X-Anonymous-Id) · 토큰은 선택. " +
                    "props 의 몸 정보 키(height · weight · age · gender · birthYear · dailyKcal 등)는 저장하지 않습니다. 기존 /logging/* 는 그대로 둡니다.")
    @PostMapping("/events")
    public ResponseEntity<CommonResponse<EventBatchResponse>> collect(Actor actor, @RequestBody EventBatchRequest request) {
        return ResponseEntity.ok(CommonResponse.success(eventService.collect(request, actor)));
    }
}
