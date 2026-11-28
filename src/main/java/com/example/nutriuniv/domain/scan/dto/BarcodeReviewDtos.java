package com.example.nutriuniv.domain.scan.dto;

import com.example.nutriuniv.domain.scan.entity.BarcodeLinkReview;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/** 운영 「확인 대기」 (SCAN_008 · SCAN_009) · 이름을 아는 바코드 적재 입출력. */
public final class BarcodeReviewDtos {

    private BarcodeReviewDtos() {}

    public record Item(Long id, String barcode, Long productId, String productName, String kind, String status,
                       boolean linkedByPicks, long pickers,
                       @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
                       @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime decidedAt,
                       Long adminUserId) {

        public static Item of(BarcodeLinkReview r, String productName) {
            return new Item(r.getId(), r.getBarcode(), r.getProductId(), productName, r.getKind().name(), r.getStatus().name(),
                    r.isLinkedByPicks(), r.getPickers(), r.getCreatedAt(), r.getDecidedAt(), r.getAdminUserId());
        }
    }

    public record Page(List<Item> items, long total, int page, int size) {}

    public record Decision(boolean confirmed) {}

    public record NameUploadResponse(int total, int created, int updated, List<String> failed) {}
}
