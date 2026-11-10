package com.example.nutriuniv.domain.analysis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /admin/analysis-requests/{requestId} 입력 — status: ANALYZING | REVIEWING | DONE | FAILED (옛 값도 받는다). */
@Getter
@NoArgsConstructor
public class AdminAnalysisRequestStatusRequest {
    private String status;
}
