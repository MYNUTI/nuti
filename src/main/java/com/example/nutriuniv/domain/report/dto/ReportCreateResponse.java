package com.example.nutriuniv.domain.report.dto;

import java.util.List;

/**
 * POST /products/{productId}/reports 출력.
 * reportIds = 새로 만든 제보. duplicateTypes = 이미 보내서 확인 중이라 새로 만들지 않은 항목. 전부 겹치면 duplicate=true.
 */
public record ReportCreateResponse(List<Long> reportIds, List<String> duplicateTypes, boolean duplicate,
                                   String status, String statusLabel, String message) {}
