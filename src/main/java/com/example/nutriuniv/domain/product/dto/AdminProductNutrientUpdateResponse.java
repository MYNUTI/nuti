package com.example.nutriuniv.domain.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/** PATCH /admin/products/{productId}/nutrients 출력 — 바뀐 필드, 판정 결과, 목표별 새 등급(즉시 재계산). */
@Getter
@Builder
public class AdminProductNutrientUpdateResponse {

    private Long productId;
    private String status;                  // ANALYZED | INSUFFICIENT
    private List<String> changedFields;     // 「sugarPer100g: 12.000 → 8.5」
    private Map<String, String> grades;     // GENERAL / WEIGHT_LOSS / MUSCLE_GAIN → A~E (INSUFFICIENT 면 빈 맵)
    private boolean manuallyCorrected;      // 재적재 보호 켜짐
}
