package com.example.nutriuniv.domain.body.dto;

import com.example.nutriuniv.domain.body.entity.BodyProfile;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** GET · PUT /me/body-profile 출력. saved=false 면 값은 모두 null 이고 kcalBand 는 기본 2000. */
public record BodyProfileResponse(
        boolean consented,
        boolean saved,
        String gender,
        Integer birthYear,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String activityLevel,
        Integer dailyKcal,
        int kcalBand,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt
) {

    public static BodyProfileResponse of(boolean consented, BodyProfile p, int defaultBand) {
        if (p == null) return new BodyProfileResponse(consented, false, null, null, null, null, null, null, defaultBand, null);
        return new BodyProfileResponse(consented, true, p.getGender().name(), p.getBirthYear(), p.getHeightCm(), p.getWeightKg(),
                p.getActivityLevel().name(), p.getDailyKcal(), p.getKcalBand(), p.getUpdatedAt());
    }
}
