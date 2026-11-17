package com.example.nutriuniv.domain.publicdata.dto;

import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;

import java.time.LocalDateTime;

/** 수집 회차 하나. */
public record PublicApiRoundResponse(
        Long roundId,
        String api,
        String label,
        String mode,             // FULL · INCREMENTAL
        String status,           // RUNNING · DONE · FAILED
        String conditionValue,   // CHNG_DT(yyyyMMdd) · EVL_YR(yyyy)
        int nextPage,
        Long totalCount,
        long receivedCount,
        int callCount,
        String lastCode,
        String lastMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static PublicApiRoundResponse from(PublicApiRound r) {
        if (r == null) return null;
        return new PublicApiRoundResponse(r.getId(), r.getApi(), r.publicApi().label(), r.getMode().name(), r.getStatus().name(),
                r.getConditionValue(), r.getNextPage(), r.getTotalCount(), r.getReceivedCount(), r.getCallCount(),
                r.getLastCode(), r.getLastMessage(), r.getStartedAt(), r.getFinishedAt());
    }
}