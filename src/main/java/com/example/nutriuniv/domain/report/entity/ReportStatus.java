package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/** 제보 처리 상태 (v3): 확인 중 / 반영함 / 반영하지 않음. 옛 PROCESSING 은 RECEIVED 로 합쳤다. */
public enum ReportStatus {
    RECEIVED("확인 중"),
    DONE("반영했어요"),
    REJECTED("반영하지 않음");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this != RECEIVED;
    }

    public static ReportStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 RECEIVED | DONE | REJECTED 중 하나여야 합니다.");
        }
        String s = raw.trim().toUpperCase();
        if ("PROCESSING".equals(s)) return RECEIVED;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status는 RECEIVED · DONE · REJECTED 중 하나여야 합니다.");
        }
    }
}
