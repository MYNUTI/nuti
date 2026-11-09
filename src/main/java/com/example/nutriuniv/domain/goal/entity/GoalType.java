package com.example.nutriuniv.domain.goal.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 목표 2가지 (v3 CORE_001~003) — 감량·근육 증가. 결과는 두 목표를 함께 내려 준다(제품당 8슬롯, product_grades.goal 이 이 값).
 * 목표를 모를 때(미설정·기기 ID 없음·공유 링크)는 감량 기준(DEFAULT). v2 의 GENERAL 은 없앴다 — 남은 행은 부팅 때 지운다(GradeEngineInitializer).
 */
public enum GoalType {
    WEIGHT_LOSS("체중 감량"),
    MUSCLE_GAIN("근육 증가");

    /** 목표를 모를 때 쓰는 기준 (v3 CORE_002 「목표가 없는 공유 진입은 감량 기준」). */
    public static final GoalType DEFAULT = WEIGHT_LOSS;

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
            throw new CustomException(ErrorCode.BAD_REQUEST, "goal은 WEIGHT_LOSS·MUSCLE_GAIN 중 하나여야 합니다.");
        }
    }
}
