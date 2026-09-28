package com.example.nutriuniv.domain.analysis.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/** 분석 대기 처리 상태 (기능명세서 10.2): 대기 / 처리중 / 완료 / 보류(단종·범위 밖). 완료 후에도 행은 지우지 않는다(수요 지표). */
public enum AnalysisRequestStatus {
    WAITING, PROCESSING, DONE, HOLD;

    public boolean isTerminal() {
        return this == DONE || this == HOLD;
    }

    public static AnalysisRequestStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 필수입니다 (WAITING | PROCESSING | DONE | HOLD).");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 WAITING·PROCESSING·DONE·HOLD 중 하나여야 합니다.");
        }
    }
}
