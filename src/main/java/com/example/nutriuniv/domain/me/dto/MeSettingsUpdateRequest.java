package com.example.nutriuniv.domain.me.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /me/settings 입력 — 둘 다 없으면 400. goal 변경은 저장만(재계산 응답 없음), 감량·근육 증가는 건강정보 동의 필요(403). */
@Getter
@NoArgsConstructor
public class MeSettingsUpdateRequest {
    private Boolean clipboardLinkDetection;
    private String goal;                    // GENERAL | WEIGHT_LOSS | MUSCLE_GAIN
}
