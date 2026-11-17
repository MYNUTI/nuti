package com.example.nutriuniv.domain.publicdata.dto;

import java.time.LocalDateTime;

/** API 하나의 수집 상태 — GET /admin/public-api/status. */
public record PublicApiStatusResponse(
        String api,
        String label,
        String provider,
        String condition,                  // NONE · CHNG_DT · EVL_YR
        PublicApiRoundResponse latestRound,
        LocalDateTime lastDoneAt,          // 마지막으로 끝까지 받은 시각 (전량 · 증분)
        LocalDateTime lastFullDoneAt,      // 마지막 전량 완료 시각
        long rawRows,                      // 원본 행 수
        int callsToday,
        int dailyLimit
) {}