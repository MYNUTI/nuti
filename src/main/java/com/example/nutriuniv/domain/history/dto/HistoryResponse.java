package com.example.nutriuniv.domain.history.dto;

import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/** GET /me/history (v3 RTN_001) — 제품별 마지막으로 본 순. product.better = 같은 중분류에 더 맞는 제품이 있는지. */
public record HistoryResponse(
        String goal,
        int kcalBand,
        List<String> grades,
        long total,
        int page,
        int size,
        boolean hasNext,
        List<Item> items
) {

    public record Item(
            ProductCard product,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime viewedAt,
            String source,
            long viewCount) {}
}
