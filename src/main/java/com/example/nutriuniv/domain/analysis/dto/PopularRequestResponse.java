package com.example.nutriuniv.domain.analysis.dto;

import java.util.List;

/** GET /analysis-requests/popular — 많이 요청됐지만 아직 분석 전인 제품 (CTRB_006). 바코드만 있는 요청은 이름이 없어 뺀다. */
public record PopularRequestResponse(List<Item> items) {

    public record Item(Long requestId, String displayName, int requestCount, String status, String statusLabel) {}
}
