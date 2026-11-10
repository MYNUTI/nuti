package com.example.nutriuniv.domain.me.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** GET /me/contributions — 내가 보낸 분석 요청 · 제보와 상태 (v3 CTRB_006). */
public record ContributionsResponse(Summary summary, List<RequestItem> requests, List<ReportItem> reports) {

    /** 요청: 분석 중(확인 중 포함) · 완료 · 분석 못 함. 제보: 확인 중 · 반영 · 반영하지 않음. */
    public record Summary(long requestCount, long requestInProgress, long requestDone, long requestFailed,
                          long reportCount, long reportReviewing, long reportDone, long reportRejected) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequestItem(Long requestId,
                              String kind,                  // BARCODE | KEYWORD | NAME | PRODUCT
                              String displayName,           // 제품 이름 · 「브랜드 이름」 · 검색어 · 바코드
                              Long productId,               // 제품이 있는 요청(영양정보 보강)이면
                              String status,                // ANALYZING | REVIEWING | DONE | FAILED
                              String statusLabel,
                              @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime requestedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportItem(Long reportId,
                             Long productId,
                             String productName,
                             String reportType,             // WRONG_PRODUCT | NAME_OR_IMAGE | NUTRITION | OTHER
                             String reportTypeLabel,
                             String status,                 // RECEIVED | DONE | REJECTED
                             String statusLabel,
                             @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt) {}
}
