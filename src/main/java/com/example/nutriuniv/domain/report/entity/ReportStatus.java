package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/** 제보 처리 상태 (기능명세서 10.4): 접수 / 처리중 / 완료(데이터 수정함) / 반려(수정 없음). */
public enum ReportStatus {
    RECEIVED, PROCESSING, DONE, REJECTED;

    public boolean isTerminal() {
        return this == DONE || this == REJECTED;
    }

    public static ReportStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 RECEIVED | PROCESSING | DONE | REJECTED 중 하나여야 합니다.");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 RECEIVED·PROCESSING·DONE·REJECTED 중 하나여야 합니다.");
        }
    }
}
