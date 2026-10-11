package com.example.nutriuniv.domain.product.dto;

import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 결과 화면의 nutrition 블록 (기능명세서 5.1) — 표시 기준(1회 제공량) 값 8종, 식이섬유는 null 가능.
 * 명세 표의 한글 키(열량·단백질…)는 공통 전제(camelCase 통일)에 따라 영문 키로 낸다.
 */
@Getter
@Builder
public class NutritionFacts {

    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal sugar;
    private BigDecimal saturatedFat;
    private BigDecimal transFat;
    private BigDecimal cholesterol;
    private BigDecimal sodium;
    private BigDecimal dietaryFiber;

    public static NutritionFacts from(ProductNutrient n) {
        if (n == null) return null;
        return NutritionFacts.builder()
                .calories(n.getCalories())
                .protein(n.getProtein())
                .sugar(n.getSugar())
                .saturatedFat(n.getSaturatedFat())
                .transFat(n.getTransFat())
                .cholesterol(n.getCholesterol())
                .sodium(n.getSodium())
                .dietaryFiber(n.getFiber())
                .build();
    }
}
