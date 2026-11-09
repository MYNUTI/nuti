package com.example.nutriuniv.domain.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** POST /admin/products (제품 적재 CSV) 결과 — 건수는 제품(master_id) 단위. */
@Getter
@Builder
public class ProductUploadResponse {

    private Long loadId;
    private int totalCount;         // 파일 속 제품 수(같은 master_id 행은 하나로 센다)
    private int successCount;       // created + updated + unchanged + protected
    private int createdCount;       // 새 제품
    private int updatedCount;       // 값이 바뀐 제품
    private int unchangedCount;     // content_hash 가 같아 건너뜀
    private int protectedCount;     // 운영자가 고친 제품이라 건너뜀(수동 수정 보호)
    private int failCount;
    private List<FailItem> failItems;

    @Getter
    @Builder
    public static class FailItem {
        private int rowNumber;      // CSV 파일 줄 번호 (헤더가 1)
        private String masterId;
        private String productName;
        private String reason;
    }
}
