package com.example.nutriuniv.domain.goal.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 사용자 목표 (기능명세서 2.4). 등급은 목표별로 따로 사전계산돼 있다(제품당 9슬롯, product_grades.goal 이 이 값).
 * 미설정은 GENERAL 로 본다. v3 에서 GENERAL 을 없애고 두 목표를 함께 계산하는 작업은 등급 브랜치에서 한다.
 */
public enum GoalType {
    GENERAL("일반"),
    WEIGHT_LOSS("체중 감량"),
    MUSCLE_GAIN("근육 증가");

    private final String label;

    GoalType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 요청 문자열 → 목표. 허용값 외 400. */
    public static GoalType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "goal은 필수입니다.");
        }
        try {
            return GoalType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "goal은 GENERAL·WEIGHT_LOSS·MUSCLE_GAIN 중 하나여야 합니다.");
        }
    }
}
