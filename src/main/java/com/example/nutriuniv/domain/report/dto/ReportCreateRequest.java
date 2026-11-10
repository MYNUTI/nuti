package com.example.nutriuniv.domain.report.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /products/{productId}/reports 입력 (v3 CTRB_008).
 * reportTypes 여러 개(고른 항목마다 한 행) — 옛 클라이언트의 reportType 하나도 받는다. content 는 OTHER 를 고르면 필수, 아니면 선택.
 * v3 는 연락처를 받지 않는다(contactEmail 이 와도 버린다).
 */
@Getter
@NoArgsConstructor
public class ReportCreateRequest {
    private List<String> reportTypes;   // WRONG_PRODUCT | NAME_OR_IMAGE | NUTRITION | OTHER (한글 이름 · 옛 값도 허용)
    private String reportType;          // (v2) 하나만
    private String content;             // 1000자
    private String barcode;             // 어떤 바코드로 본 제품에서 제보했는지(선택)
}
