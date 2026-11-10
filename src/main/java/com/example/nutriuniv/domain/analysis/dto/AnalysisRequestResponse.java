package com.example.nutriuniv.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * POST /analysis-requests 출력.
 * alreadyRequested = 먼저 요청한 기기가 있었다(이번 요청이 첫 요청이 아님). 같은 기기의 거듭 요청은 requestCount 를 올리지 않는다.
 */
@Getter
@Builder
public class AnalysisRequestResponse {
    private Long requestId;
    private String type;
    private int requestCount;
    private String status;              // ANALYZING | REVIEWING | DONE | FAILED
    private String statusLabel;         // 분석 중 · 확인 중 · 완료 · 분석 못 함
    private boolean alreadyRequested;
    private String message;
}
