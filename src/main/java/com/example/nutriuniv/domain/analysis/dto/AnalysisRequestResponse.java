package com.example.nutriuniv.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

/** POST /analysis-requests 출력 — 같은 식별값 재접수면 requestCount 만 올라간 기존 항목이 돌아온다. */
@Getter
@Builder
public class AnalysisRequestResponse {
    private Long requestId;
    private String type;
    private int requestCount;
    private String status;
}
