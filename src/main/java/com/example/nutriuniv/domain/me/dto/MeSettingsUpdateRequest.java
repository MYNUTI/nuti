package com.example.nutriuniv.domain.me.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /me/settings 입력 — goal 이 없으면 400. goal 변경은 저장만(재계산 응답 없음). */
@Getter
@NoArgsConstructor
public class MeSettingsUpdateRequest {
    private String goal;                    // GENERAL | WEIGHT_LOSS | MUSCLE_GAIN
}
