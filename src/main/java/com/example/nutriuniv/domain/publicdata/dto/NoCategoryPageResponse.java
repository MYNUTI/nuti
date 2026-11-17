package com.example.nutriuniv.domain.publicdata.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 분류가 없는 공공데이터 제품 — 검토 목록. 분류를 채울 사람이 본다. */
public record NoCategoryPageResponse(
        long total,
        int page,
        int size,
        List<Item> items
) {
    public record Item(Long productId, String masterId, String source, String name, String maker, LocalDateTime createdAt) {}
}