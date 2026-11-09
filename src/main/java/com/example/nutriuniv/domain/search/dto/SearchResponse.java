package com.example.nutriuniv.domain.search.dto;

import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * GET /search (v3 SRCH_001 · SRCH_005) — 묶음 없이 한 목록.
 * <ul>
 *   <li>pinned: 검색어가 제품 이름 전체와 같은 제품 — 정렬과 상관없이 맨 위 한 칸 (첫 페이지에서만, items 에는 없다)</li>
 *   <li>category: 검색어가 중분류 이름과 같으면 「○○ 순위로 보기」</li>
 *   <li>total: pinned 포함 「검색 결과 n개」</li>
 *   <li>0건이면 didYouMean · sameCategoryTop(추정 종류의 점수 상위, 없으면 빈 배열)</li>
 * </ul>
 */
public record SearchResponse(
        String query,
        String goal,
        int kcalBand,
        String sort,
        String sortLabel,
        long total,
        int page,
        int size,
        boolean hasNext,
        @JsonInclude(JsonInclude.Include.NON_NULL) ProductCard pinned,
        @JsonInclude(JsonInclude.Include.NON_NULL) CategoryLink category,
        List<ProductCard> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) String didYouMean,
        List<ProductCard> sameCategoryTop,
        boolean analysisRequestAvailable
) {

    public record CategoryLink(String code, String name) {}
}
