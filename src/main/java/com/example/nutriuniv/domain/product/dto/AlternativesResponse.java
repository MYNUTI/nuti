package com.example.nutriuniv.domain.product.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /products/{id}/alternatives (v3 COMP_001) — 같은 중분류에서 goal 점수가 더 높은 제품, 점수 높은 순.
 * diffs: 후보 − 지금 제품, 100g(음료 100mL)당 성분 2개 — 목표 기준으로 가장 나아진 두 성분.
 */
public record AlternativesResponse(
        ProductCard current,
        String goal,
        int kcalBand,
        long total,
        int page,
        int size,
        boolean hasNext,
        List<Alternative> items
) {

    public record Alternative(ProductCard product, List<Diff> diffs) {}

    /** text 예: 「칼로리 −29kcal」「당류 −2.8g」. */
    public record Diff(String nutrient, String label, String unit, BigDecimal diff, String text) {}
}
