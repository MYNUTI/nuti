package com.example.nutriuniv.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /admin/reports 출력 (v3) — 최근 접수순. 연락처는 내보내지 않는다. */
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

        private String reportType;          // WRONG_PRODUCT | NAME_OR_IMAGE | NUTRITION | OTHER
        private String reportTypeLabel;
        private String content;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String barcode;             // 어떤 바코드로 본 제품에서 제보했는지

        private String status;              // RECEIVED | DONE | REJECTED
        private String statusLabel;         // 확인 중 · 반영했어요 · 반영하지 않음

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String adminMemo;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime handledAt;
    }
}
