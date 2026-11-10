package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

import java.util.Map;

/**
 * 제보 항목 (v3 CTRB_008) — 여러 개 고를 수 있고, 고른 항목마다 한 행.
 * 옛 값 NAME · IMAGE 는 NAME_OR_IMAGE 로 합쳤다(부팅 때 V3EnumMigration 이 옮김). 코드 · 한글 이름 · 옛 값 모두 받는다.
 */
public enum ReportType {
    WRONG_PRODUCT("다른 제품이에요"),
    NAME_OR_IMAGE("이름이나 사진이 달라요"),
    NUTRITION("영양정보가 달라요"),
    OTHER("기타");

    /** v2 → v3 값. */
    public static final Map<String, ReportType> LEGACY = Map.of(
            "NAME", NAME_OR_IMAGE, "IMAGE", NAME_OR_IMAGE, "제품명", NAME_OR_IMAGE, "이미지", NAME_OR_IMAGE, "영양정보", NUTRITION);

    private final String label;

    ReportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 데이터 수정을 전제하는 항목인가 — DONE 처리 시 재적재 보호 플래그를 켠다. */
    public boolean correctsData() {
        return this != OTHER;
    }

    public static ReportType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "제보 항목은 필수입니다 (WRONG_PRODUCT | NAME_OR_IMAGE | NUTRITION | OTHER).");
        }
        String t = raw.trim();
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(t) || type.label.equals(t)) return type;
        }
        ReportType legacy = LEGACY.get(t.toUpperCase());
        if (legacy == null) legacy = LEGACY.get(t);
        if (legacy != null) return legacy;
        throw new CustomException(ErrorCode.BAD_REQUEST, "제보 항목은 WRONG_PRODUCT · NAME_OR_IMAGE · NUTRITION · OTHER 중에서 골라야 합니다.");
    }
}
