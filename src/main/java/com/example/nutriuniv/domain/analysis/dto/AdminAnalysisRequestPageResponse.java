package com.example.nutriuniv.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /admin/analysis-requests 출력 (기능명세서 10.2) — 기본 정렬 요청 횟수 많은 순(수요 우선). */
@Getter
@Builder
public class AdminAnalysisRequestPageResponse {

    private List<Item> items;
    private long totalCount;
    private int page;
    private int size;

    @Getter
    @Builder
    public static class Item {
        private Long requestId;
        private String type;                // NEW_PRODUCT | NUTRITION_FILL
        private String identifier;          // 바코드 · 검색어 · 제품 ID

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long productId;             // NUTRITION_FILL 일 때

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String productName;         // NUTRITION_FILL 일 때

        private int requestCount;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime firstRequestedAt;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastRequestedAt;

        private String status;              // WAITING | PROCESSING | DONE | HOLD

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime processedAt;
    }
}
