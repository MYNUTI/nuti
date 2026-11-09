package com.example.nutriuniv.domain.saved.dto;

import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /me/saved-products (v3 COMP_006) — 저장한 순(최근 먼저). 단종 · 생산 중단 제품도 상태 배지(product.state)로 남긴다.
 * 점수가 없는 제품은 product.goals 빈 배열. product.better = 같은 중분류에 더 맞는 제품이 있는지(요청 목표 기준).
 */
public record SavedProductPageResponse(
        String goal,
        int kcalBand,
        long totalCount,
        boolean hasNext,
        List<Item> items
) {

    public record Item(
            ProductCard product,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime savedAt) {}
}
