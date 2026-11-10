package com.example.nutriuniv.domain.analysis.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

import java.util.Map;

/**
 * 분석 요청 상태 (v3): 분석 중 → 확인 중 → 완료 / 분석 못 함. 완료 후에도 행은 지우지 않는다(수요 지표).
 * 옛 값(WAITING · PROCESSING · HOLD)은 부팅 때 V3EnumMigration 이 옮기고, 입력으로 와도 새 값으로 받는다.
 */
public enum AnalysisRequestStatus {
    ANALYZING("분석 중"),
    REVIEWING("확인 중"),
    DONE("완료"),
    FAILED("분석 못 함");

    /** v2 → v3 값. */
    public static final Map<String, AnalysisRequestStatus> LEGACY = Map.of(
            "WAITING", ANALYZING, "PROCESSING", REVIEWING, "HOLD", FAILED);

    private final String label;

    AnalysisRequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == DONE || this == FAILED;
    }

    public static AnalysisRequestStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 필수입니다 (ANALYZING | REVIEWING | DONE | FAILED).");
        }
        String s = raw.trim().toUpperCase();
        if (LEGACY.containsKey(s)) return LEGACY.get(s);
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 ANALYZING·REVIEWING·DONE·FAILED 중 하나여야 합니다.");
        }
    }
}
