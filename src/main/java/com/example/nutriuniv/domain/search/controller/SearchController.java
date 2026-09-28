package com.example.nutriuniv.domain.search.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.search.dto.PopularKeywordResponse;
import com.example.nutriuniv.domain.search.dto.SearchResponse;
import com.example.nutriuniv.domain.search.dto.SearchSuggestionResponse;
import com.example.nutriuniv.domain.search.service.ProductSearchService;
import com.example.nutriuniv.domain.search.service.SearchService;
import com.example.nutriuniv.domain.search.service.SearchSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Search", description = "검색 API")
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ProductSearchService productSearchService;
    private final SearchSuggestionService searchSuggestionService;

    // GET /search
    @Operation(summary = "검색 (기능명세서 6.1)",
            description = "4-way: 이름 그대로 포함 · 철자 유사도(pg_trgm) · 공백/특수문자 제거 대조 · 초성(ㄷㄷㅂ→더단백) + 동의어 사전. " +
                    "정확 일치와 비슷한 제품을 나눠서, 각각 등급순(영양정보 부족은 맨 뒤). 각 결과에 목표 기준 위반 문구(highlight). " +
                    "0건도 200 — didYouMean(교정 결과가 실존하고 편집거리 ≤ 2일 때만)·sameCategoryTop(0건일 때 상위 5)·analysisRequest.available. " +
                    "빈 검색어·30자 초과 400. goal 생략 시 내 목표(없으면 GENERAL). page 0부터, size 1~50.")
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<SearchResponse>> search(
            Actor actor,
            @Parameter(description = "검색어 (최대 30자)") @RequestParam(required = false) String q,
            @Parameter(description = "GENERAL | WEIGHT_LOSS | MUSCLE_GAIN (선택)") @RequestParam(required = false) String goal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(productSearchService.search(q, goal, page, size, actor)));
    }

    // GET /search/suggestions
    @Operation(summary = "검색 자동완성 (기능명세서 6.2)",
            description = "초성·공백 무시 색인으로 접두→포함 순, 모자라면 사전 대표어(KEYWORD)·철자 유사도(오타 보정). " +
                    "1자 이하는 빈 배열 200, 30자 초과 400. matchedRanges 는 name 에서 매치된 [start,end) 구간. size 기본 10, 최대 20.")
    @GetMapping("/search/suggestions")
    public ResponseEntity<CommonResponse<SearchSuggestionResponse>> suggestions(
            @Parameter(description = "입력 중인 검색어") @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(CommonResponse.success(searchSuggestionService.suggest(q, size)));
    }

    // GET /search/keywords/popular
    @Operation(summary = "인기 검색어 조회",
            description = "오늘 검색 횟수 기준 인기 검색어를 rank 순으로 최대 10개 반환합니다.")
    @GetMapping("/search/keywords/popular")
    public ResponseEntity<CommonResponse<List<PopularKeywordResponse>>> getPopularKeywords() {
        return ResponseEntity.ok(CommonResponse.success(searchService.getPopularKeywords()));
    }

    // GET /search/keywords/recent      — 보류 (현 화면 미노출). 재개 시 익명 ID 기준으로 완화 예정.
    // GET /search/keywords/recommended — 3차 보류 (추천 위치 미정). RecommendationService는 보존.
}
