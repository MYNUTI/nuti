package com.example.nutriuniv.domain.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /search 출력 (기능명세서 6.1). 0건도 200 — 폴백 4단계(didYouMean · 분석해 보기 · sameCategoryTop · 찾는 제품 알려주기)를 응답에 담는다.
 * 영양정보 부족 제품도 나온다(status=INSUFFICIENT, grade 없음 → 클라가 「분석 전」). 정렬은 등급순, 부족 제품은 맨 뒤.
 */
@Getter
@Builder
public class SearchResponse {

    private String query;
    private String appliedGoal;
    private Section exactMatches;               // 그대로 포함 · 공백/특수문자 제거 · 초성 · 브랜드명
    private Section similarProducts;            // 철자 유사도 · 동의어

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String didYouMean;                  // 교정 결과가 실존하고 편집거리 ≤ 2 일 때만

    private List<Item> sameCategoryTop;         // 0건일 때만 채움 (가장 비슷한 제품의 분류에서 등급 상위 5)
    private AnalysisRequest analysisRequest;

    @Getter
    @Builder
    public static class Section {
        private long total;
        private List<Item> items;
    }

    @Getter
    @Builder
    public static class Item {
        private Long productId;
        private String name;
        private String brandName;
        private String imageUrl;
        private String status;                  // ANALYZED | INSUFFICIENT

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String grade;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String gradeLabel;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String highlight;               // 「당류 21g — 체중 감량 기준 초과」

        @JsonProperty("isSaved")                // Lombok boolean getter(isSaved) 를 Jackson 이 'saved' 로 줄이는 것 방지
        private boolean saved;
    }

    @Getter
    @Builder
    public static class AnalysisRequest {
        private boolean available;              // 0건일 때 true — 「찾으시는 제품을 알려주세요」(POST /analysis-requests keyword)
    }
}
