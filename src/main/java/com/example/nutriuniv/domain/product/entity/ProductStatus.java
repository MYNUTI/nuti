package com.example.nutriuniv.domain.product.entity;

import java.math.BigDecimal;

/**
 * 제품 상태 두 가지 (기능명세서 공통 규칙·4.1). 이 용어를 그대로 쓴다.
 * <ul>
 *   <li>ANALYZED(분석 완료) — 판정 7종(열량·단백질·당류·포화지방·트랜스지방·콜레스테롤·나트륨)의 100g 기준값이 전부 있음 → 등급 + 감점 이유</li>
 *   <li>INSUFFICIENT(영양정보 부족) — 하나라도 결측 → 제품명·이미지만, 등급 없음, 분석 대기 목록(NUTRITION_FILL) 등록</li>
 * </ul>
 * 식이섬유는 판정에 넣지 않는다(없으면 0 으로 계산). products.status 컬럼은 영양정보 저장 시와 등급 배치가 이 판정으로 갱신한다.
 */
public enum ProductStatus {
    ANALYZED("분석 완료"),
    INSUFFICIENT("영양정보 부족");

    private final String label;

    ProductStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 영양정보 행이 없거나 판정 7종 중 결측이 있으면 INSUFFICIENT. */
    public static ProductStatus of(ProductNutrient n) {
        if (n == null) return INSUFFICIENT;
        return isComplete(n.getCaloriesPer100g(), n.getProteinPer100g(), n.getSugarPer100g(),
                n.getSaturatedFatPer100g(), n.getTransFatPer100g(), n.getCholesterolPer100g(), n.getSodiumPer100g())
                ? ANALYZED : INSUFFICIENT;
    }

    /** 판정 7종 전부 non-null 인가. */
    public static boolean isComplete(BigDecimal... required) {
        for (BigDecimal v : required) {
            if (v == null) return false;
        }
        return true;
    }
}
