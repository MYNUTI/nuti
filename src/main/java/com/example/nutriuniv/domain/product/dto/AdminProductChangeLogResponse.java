package com.example.nutriuniv.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /admin/products/{productId}/change-logs 출력 — 최근순. */
@Getter
@Builder
public class AdminProductChangeLogResponse {

    private Long productId;
    private boolean manuallyCorrected;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime correctedAt;

    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private Long id;
        private String source;              // ADMIN_UPDATE | ADMIN_NUTRIENT_UPDATE | REPORT_DONE

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long reportId;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long adminUserId;

        private String summary;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
