package com.example.nutriuniv.domain.analysis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /admin/analysis-requests/{requestId} 입력 — status: WAITING | PROCESSING | DONE | HOLD. */
@Getter
@NoArgsConstructor
public class AdminAnalysisRequestStatusRequest {
    private String status;
}
