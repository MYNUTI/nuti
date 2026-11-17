package com.example.nutriuniv.domain.publicdata.dto;

import java.util.List;

/** 수집 한 번 돌리기 결과 — 새로 연 회차 · 이어 받은 회차. */
public record TickResponse(
        List<PublicApiRoundResponse> started,
        List<PublicApiRoundResponse> stepped,
        List<String> skipped,      // 낮이라 미룬 회차 등
        List<String> errors
) {}