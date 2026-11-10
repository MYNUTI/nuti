package com.example.nutriuniv.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** GET /admin/analysis-requests 출력 (ADM_002) — 기본 정렬 요청 수 많은 순(수요 우선). statusCounts 는 필터와 상관없이 전체. */
@Getter
@Builder
public class AdminAnalysisRequestPageResponse {

    private List<Item> items;
    private Map<String, Long> statusCounts;     // ANALYZING · REVIEWING · DONE · FAILED
    private long totalCount;
    private int page;
    private int size;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private Long requestId;
        private String type;                // NEW_PRODUCT | NUTRITION_FILL
        private String kind;                // BARCODE | KEYWORD | NAME | PRODUCT
        private String identifier;          // 바코드 · 검색어 · 「브랜드 이름」 · 제품 ID
        private Long productId;             // NUTRITION_FILL
        private String productName;         // 제품명 요청의 이름, 또는 NUTRITION_FILL 제품 이름
        private String brandName;           // 제품명 요청
        private String originBarcode;       // 제품명 요청 — 못 찾은 바코드
        private String originKeyword;       // 제품명 요청 — 결과 없는 검색어
        private int requestCount;           // 요청한 기기 수

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime firstRequestedAt;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastRequestedAt;

        private String status;              // ANALYZING | REVIEWING | DONE | FAILED
        private String statusLabel;         // 분석 중 · 확인 중 · 완료 · 분석 못 함

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime processedAt;
    }
}
