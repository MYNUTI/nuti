package com.example.nutriuniv.domain.product.enums;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 영양성분 필터 칩 6개 (v3 SRCH_008) — 식약처 영양강조표시 기준, 100g(음료 100mL)당.
 * 음료는 1회 제공량 단위가 mL · L 인 제품. 숨김 규칙: 그 종류에서 해당 제품이 85% 를 넘거나 10개 미만이면 숨긴다.
 */
public enum FilterChip {
    LOW_SUGAR("저당", "당류 5g(음료 2.5g) 미만"),
    ZERO_SUGAR("제로 슈거", "당류 0.5g 미만"),
    LOW_CALORIE("저칼로리", "40kcal(음료 20kcal) 미만"),
    ZERO_CALORIE("제로 칼로리", "음료 100mL당 4kcal 미만"),
    HIGH_PROTEIN("고단백", "단백질 11g(음료 5.5g) 이상, 또는 100kcal당 5.5g 이상"),
    LOW_FAT("저지방", "지방 3g(음료 1.5g) 미만");

    public static final double HIDE_ABOVE_RATIO = 0.85;
    public static final int HIDE_BELOW_COUNT = 10;

    /** 음료 판정 — 1회 제공량 문자열에 숫자 + mL/L. */
    static final String DRINK = "(COALESCE(pn.serving_size, '') ~* '[0-9][[:space:]]*(ml|l)([^a-z]|$)')";

    private final String label;
    private final String rule;

    FilterChip(String label, String rule) {
        this.label = label;
        this.rule = rule;
    }

    public String label() { return label; }
    public String rule() { return rule; }

    /** WHERE 조각 (별칭 pn = product_nutrients). 값이 없으면 해당하지 않는다. */
    public String sql() {
        return switch (this) {
            case LOW_SUGAR -> "pn.sugar_per_100g < CASE WHEN " + DRINK + " THEN 2.5 ELSE 5 END";
            case ZERO_SUGAR -> "pn.sugar_per_100g < 0.5";
            case LOW_CALORIE -> "pn.calories_per_100g < CASE WHEN " + DRINK + " THEN 20 ELSE 40 END";
            case ZERO_CALORIE -> "(" + DRINK + " AND pn.calories_per_100g < 4)";
            case HIGH_PROTEIN -> "(pn.protein_per_100g >= CASE WHEN " + DRINK + " THEN 5.5 ELSE 11 END"
                    + " OR (pn.calories_per_100g > 0 AND pn.protein_per_100g * 100 / pn.calories_per_100g >= 5.5))";
            case LOW_FAT -> "pn.fat_per_100g < CASE WHEN " + DRINK + " THEN 1.5 ELSE 3 END";
        };
    }

    public static FilterChip from(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM,
                    "nutrients는 LOW_SUGAR·ZERO_SUGAR·LOW_CALORIE·ZERO_CALORIE·HIGH_PROTEIN·LOW_FAT 중에서 고릅니다.");
        }
    }
}
