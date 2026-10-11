package com.example.nutriuniv.domain.report.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /products/{productId}/reports 입력 — reportType·content 필수, contactEmail 선택. */
@Getter
@NoArgsConstructor
public class ReportCreateRequest {
    private String reportType;      // NUTRITION | NAME | IMAGE | OTHER (한글 이름도 허용: 영양정보·제품명·이미지·기타)
    private String content;
    private String contactEmail;
}
