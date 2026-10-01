package com.example.nutriuniv.domain.report.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /admin/reports/{reportId} 입력 — status·memo 둘 다 선택(둘 다 없으면 400). */
@Getter
@NoArgsConstructor
public class AdminReportUpdateRequest {
    private String status;          // RECEIVED | PROCESSING | DONE | REJECTED
    private String memo;
}
