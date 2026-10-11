package com.example.nutriuniv.domain.analysis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /analysis-requests 입력 (기능명세서 10.2 사용자 접수).
 * NEW_PRODUCT 는 barcode 또는 keyword, NUTRITION_FILL 은 productId — 유형별 식별값 누락은 400.
 */
@Getter
@NoArgsConstructor
public class AnalysisRequestCreateRequest {
    private String type;        // NEW_PRODUCT | NUTRITION_FILL
    private String barcode;     // 8·12·13·14자리 → 13자리 정규화
    private String keyword;     // 최대 30자
    private Long productId;
}
