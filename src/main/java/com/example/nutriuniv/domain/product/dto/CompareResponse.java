package com.example.nutriuniv.domain.product.dto;

import java.util.List;

/**
 * GET /products/compare (v3 COMP_002) — 2~4개를 요청 순서대로. 카드(두 목표 점수 · 등급) + 1회 제공량 + 성분표 10줄.
 * nutrients[].per100g · perServing 의 value 가 null 이면 표시 없음(0 과 구분).
 */
public record CompareResponse(
        String goal,
        int kcalBand,
        List<Item> items
) {

    public record Item(
            ProductCard product,
            ProductResultResponse.Serving serving,      // 1회 제공량이 없으면 null
            String basis,                               // 100g | 100mL
            List<ProductResultResponse.NutrientRow> nutrients) {}
}
