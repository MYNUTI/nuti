package com.example.nutriuniv.domain.body.dto;

import java.math.BigDecimal;

/**
 * PUT /me/body-profile 입력 (ACCT_010). 하루 필요 칼로리는 기기가 계산한 값 — 칼로리 구간은 서버가 이 값으로 정한다.
 * gender: MALE | FEMALE, activityLevel: SEDENTARY | LOW_ACTIVE | ACTIVE | VERY_ACTIVE.
 */
public record BodyProfileRequest(
        String gender,
        Integer birthYear,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String activityLevel,
        Integer dailyKcal
) {}
