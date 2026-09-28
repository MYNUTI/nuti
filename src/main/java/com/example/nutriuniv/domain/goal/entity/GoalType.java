package com.example.nutriuniv.domain.goal.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 사용자 목표 (기능명세서 2.4). 등급은 목표별로 따로 사전계산돼 있다(제품당 9슬롯).
 * 감량·근육증가는 건강정보 동의(1.3)가 전제. 미설정은 GENERAL 로 본다.
 */
public enum GoalType {
    GENERAL("일반", "health"),
    WEIGHT_LOSS("체중 감량", "diet"),
    MUSCLE_GAIN("근육 증가", "bulk");

    private final String label;
    private final String pnsGoal;

    GoalType(String label, String pnsGoal) {
        this.label = label;
        this.pnsGoal = pnsGoal;
    }

    public String label() {
        return label;
    }

    /** 기존 pns 사전계산 테이블(product_pns_by_eer)의 goal 값. 등급 엔진 개조 시 GoalType 으로 통일 예정. */
    public String pnsGoal() {
        return pnsGoal;
    }

    public boolean requiresHealthConsent() {
        return this != GENERAL;
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
