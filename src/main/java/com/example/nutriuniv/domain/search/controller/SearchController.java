package com.example.nutriuniv.domain.search.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.search.dto.PopularKeywordResponse;
import com.example.nutriuniv.domain.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Search", description = "검색 API")
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

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
