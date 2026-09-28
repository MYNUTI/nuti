package com.example.nutriuniv.domain.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /search/suggestions 출력 (기능명세서 6.2). type=PRODUCT(제품 제안, productId 있음) | KEYWORD(사전 대표어 제안).
 * matchedRanges 는 name 에서 질의가 매치된 [start, end) 구간(하이라이트용). 유사도로 잡힌 제안은 빈 배열.
 */
@Getter
@Builder
public class SearchSuggestionResponse {

    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private String type;                    // PRODUCT | KEYWORD

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long productId;

        private String name;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String brandName;

        private List<List<Integer>> matchedRanges;
    }
}
