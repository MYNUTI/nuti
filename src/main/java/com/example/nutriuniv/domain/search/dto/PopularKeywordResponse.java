package com.example.nutriuniv.domain.search.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /search/keywords/popular (v3 SRCH_009) — 인기 검색어 최대 10개.
 * basedAt: 집계 기준 시각(정각, 화면의 「11.05 16:00 기준」). 아직 집계 전이면 null.
 */
public record PopularKeywordResponse(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime basedAt,
        List<Item> keywords
) {

    public record Item(int rank, String keyword) {}

    public static PopularKeywordResponse empty() {
        return new PopularKeywordResponse(null, List.of());
    }
}
