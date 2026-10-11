package com.example.nutriuniv.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /admin/reports 출력 (기능명세서 10.4) — 최근 접수순. */
@Getter
@Builder
public class AdminReportPageResponse {

    private List<Item> items;
    private long totalCount;
    private int page;
    private int size;

    @Getter
    @Builder
    public static class Item {
        private Long reportId;
        private Long productId;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String productName;

        private String reportType;          // NUTRITION | NAME | IMAGE | OTHER
        private String reportTypeLabel;     // 영양정보 · 제품명 · 이미지 · 기타
        private String content;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String contactEmail;

        private String status;              // RECEIVED | PROCESSING | DONE | REJECTED

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String adminMemo;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime handledAt;
    }
}
