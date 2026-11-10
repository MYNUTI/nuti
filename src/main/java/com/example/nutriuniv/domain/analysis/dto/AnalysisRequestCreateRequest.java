package com.example.nutriuniv.domain.analysis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /analysis-requests 입력.
 * <ul>
 *   <li>제품명 요청(v3 CTRB): productName 필수 · brandName 선택 · 어디서 왔는지 originBarcode / originKeyword 선택. type 은 비워도 된다(NEW_PRODUCT)</li>
 *   <li>NEW_PRODUCT: barcode 또는 keyword</li>
 *   <li>NUTRITION_FILL: productId</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
public class AnalysisRequestCreateRequest {
    private String type;            // NEW_PRODUCT | NUTRITION_FILL (제품명 요청이면 생략 가능)
    private String barcode;         // 8·12·13·14자리 → 13자리 정규화
    private String keyword;         // 최대 30자
    private Long productId;
    private String productName;     // 제품명 요청 — 최대 100자
    private String brandName;       // 제품명 요청 — 최대 50자
    private String originBarcode;
    private String originKeyword;
}
