package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/** 오류 제보 유형 (기능명세서 10.4): 영양정보 · 제품명 · 이미지 · 기타. 코드와 한글 이름 둘 다 받는다. */
public enum ReportType {
    NUTRITION("영양정보"),
    NAME("제품명"),
    IMAGE("이미지"),
    OTHER("기타");

    private final String label;

    ReportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 데이터 수정을 전제하는 유형인가 — DONE 처리 시 재적재 보호 플래그를 켠다. */
    public boolean correctsData() {
        return this != OTHER;
    }

    public static ReportType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "reportType은 필수입니다 (NUTRITION | NAME | IMAGE | OTHER).");
        }
        String t = raw.trim();
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(t) || type.label.equals(t)) return type;
        }
        throw new CustomException(ErrorCode.BAD_REQUEST, "reportType은 NUTRITION(영양정보)·NAME(제품명)·IMAGE(이미지)·OTHER(기타) 중 하나여야 합니다.");
    }
}
