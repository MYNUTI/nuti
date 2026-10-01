package com.example.nutriuniv.domain.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PATCH /admin/products/{productId}/nutrients 입력 — 전달된 필드만 바꾼다(null 유지). 단위: 열량 kcal, 콜레스테롤·나트륨 mg, 나머지 g.
 * 등급 계산에 쓰는 것은 *Per100g 8종(판정 7종 + 식이섬유). 1회 제공량 값은 결과 화면 표시용.
 */
@Getter
@NoArgsConstructor
public class AdminProductNutrientUpdateRequest {

    private String servingSize;

    private BigDecimal calories;
    private BigDecimal carbohydrate;
    private BigDecimal sugar;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal saturatedFat;
    private BigDecimal transFat;
    private BigDecimal cholesterol;
    private BigDecimal sodium;
    private BigDecimal fiber;

    private BigDecimal caloriesPer100g;
    private BigDecimal carbohydratePer100g;
    private BigDecimal sugarPer100g;
    private BigDecimal proteinPer100g;
    private BigDecimal fatPer100g;
    private BigDecimal saturatedFatPer100g;
    private BigDecimal transFatPer100g;
    private BigDecimal cholesterolPer100g;
    private BigDecimal sodiumPer100g;
    private BigDecimal fiberPer100g;

    private Long reportId;          // 제보 처리에서 비롯된 수정이면 (수정 이력에 연결)
    private String memo;            // 수정 이력 메모 (선택)
}
