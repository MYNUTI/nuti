package com.example.nutriuniv.domain.grade.entity;

import java.util.List;

/**
 * 등급 계산 입력 8종 (기능명세서 4.1).
 * 「분석 완료」 판정은 7종(식이섬유 제외) — 식이섬유는 없으면 0 으로 계산하고 fiberIncluded=false 로 표시한다.
 */
public enum Nutrient {
    CALORIES("열량", "kcal", true),
    PROTEIN("단백질", "g", true),
    DIETARY_FIBER("식이섬유", "g", false),
    SUGAR("당류", "g", true),
    SATURATED_FAT("포화지방", "g", true),
    TRANS_FAT("트랜스지방", "g", true),
    CHOLESTEROL("콜레스테롤", "mg", true),
    SODIUM("나트륨", "mg", true);

    private final String label;
    private final String unit;
    private final boolean analysisRequired;

    Nutrient(String label, String unit, boolean analysisRequired) {
        this.label = label;
        this.unit = unit;
        this.analysisRequired = analysisRequired;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    /** 분석 완료 판정 7종에 포함되는가 (식이섬유만 false). */
    public boolean isAnalysisRequired() {
        return analysisRequired;
    }

    public static List<Nutrient> analysisRequired() {
        return List.of(CALORIES, PROTEIN, SUGAR, SATURATED_FAT, TRANS_FAT, CHOLESTEROL, SODIUM);
    }
}
