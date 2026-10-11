package com.example.nutriuniv.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /products/barcode/{barcode} 출력 (기능명세서 3.1·3.2).
 * status=INSUFFICIENT 면 grade·topReason 이 없다(제품명·이미지만) — 클라는 「아직 분석 전이에요」.
 */
@Getter
@Builder
public class BarcodeScanResponse {

    private String status;              // ANALYZED | INSUFFICIENT
    private ProductSummary product;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private GradeBadge grade;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String topReason;

    private String appliedGoal;
}
