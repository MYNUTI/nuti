package com.example.nutriuniv.domain.report.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** POST /products/{productId}/reports 출력 — 접수 번호. */
@Getter
@AllArgsConstructor
public class ReportCreateResponse {
    private Long reportId;
    private String status;          // RECEIVED
}
