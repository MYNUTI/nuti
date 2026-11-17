package com.example.nutriuniv.domain.search.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.search.dto.PopularKeywordResponse;
import com.example.nutriuniv.domain.search.dto.SearchResponse;
import com.example.nutriuniv.domain.search.dto.SearchSuggestionResponse;
import com.example.nutriuniv.domain.search.service.PopularKeywordService;
import com.example.nutriuniv.domain.search.service.ProductSearchService;
import com.example.nutriuniv.domain.search.service.SearchSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "검색 API")
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchService productSearchService;
    private final SearchSuggestionService searchSuggestionService;
    private final PopularKeywordService popularKeywordService;

    // GET /search
    @Operation(summary = "이름 검색 (v3 SRCH_001)",
            description = "이름 · 브랜드 · 공백 무시 · 초성 일치와 비슷한 이름(pg_trgm) · 동의어를 묶음 없이 한 목록으로 줍니다. " +
                    "sort: SCORE(기본, 점수 높은 순 → 이름순, 점수 없음 맨 뒤) · POPULAR · CALORIES_LOW · PROTEIN_HIGH · SUGAR_LOW(100g·100mL당, 값 없음 맨 뒤). " +
                    "검색어가 제품 이름 전체와 같으면 pinned(첫 페이지 맨 위 한 칸), 중분류 이름과 같으면 category. " +
                    "카드는 두 목표 점수 · 등급과 goal 기준 태그 2개(영양소 정렬이면 sortValue). 단종 · 생산 중단도 배지로 포함. " +
                    "goal 은 저장하지 않습니다(없으면 WEIGHT_LOSS). 0건이면 didYouMean · sameCategoryTop. page 0부터, size 기본 20.")
    @GetMapping("/search")
    public ResponseEntity<CommonResponse<SearchResponse>> search(
            Actor actor,
            @Parameter(description = "검색어 (최대 30자)") @RequestParam(required = false) String q,
            @Parameter(description = "WEIGHT_LOSS | MUSCLE_GAIN (선택)") @RequestParam(required = false) String goal,
            @Parameter(description = "SCORE | POPULAR | CALORIES_LOW | PROTEIN_HIGH | SUGAR_LOW") @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(productSearchService.search(q, goal, sort, page, size, actor)));
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
    @Operation(summary = "인기 검색어 (v3 SRCH_009)",
            description = "최근 7일 동안 그 말을 검색한 사람 수가 많은 순으로 최대 10개(3명 미만 · 결과 0건 검색은 빼요). " +
                    "매시 정각에 다시 모으고, basedAt 은 그 기준 시각(아직 모으기 전이면 null · keywords 빈 배열). 급상승 검색어는 출시 때 숨겨서 없습니다.")
    @GetMapping("/search/keywords/popular")
    public ResponseEntity<CommonResponse<PopularKeywordResponse>> popularKeywords() {
        return ResponseEntity.ok(CommonResponse.success(popularKeywordService.popular()));
    }

    // GET /search/keywords/recent      — 보류 (현 화면 미노출). 재개 시 익명 ID 기준으로 완화 예정.
    // GET /search/keywords/recommended — 3차 보류 (추천 위치 미정). RecommendationService는 보존.
}
