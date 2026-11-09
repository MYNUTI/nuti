package com.example.nutriuniv.domain.ranking.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.BrandListResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.CategoriesResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.FilterSheetResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.OverviewResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.RankingResponse;
import com.example.nutriuniv.domain.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Ranking", description = "랭킹 · 필터 (v3 SRCH_007 · SRCH_008) — 조회할 때 계산, 광고·제휴는 순서에 넣지 않음")
@RestController
@RequiredArgsConstructor
public class RankingController {

    private static final String FILTER_DOC = " 필터: brands(브랜드 줄 이름, 「제조 ○○」 포함) · nutrients(LOW_SUGAR·ZERO_SUGAR·LOW_CALORIE·ZERO_CALORIE·HIGH_PROTEIN·LOW_FAT) · "
            + "sellers(COUPANG·CU·GS25·SEVEN_ELEVEN·EMART24·OLIVE_YOUNG·KURLY — 지금 자료가 있는 곳은 COUPANG 뿐). 탭끼리 AND, 탭 안 OR.";

    private final RankingService rankingService;

    @Operation(summary = "랭킹 분류 (대분류 15 + 열린 칩)",
            description = "대분류 15개는 늘 줍니다. chips 는 분석 완료 300개 이상으로 순위가 열린 중분류·합산 칩만, 탭 순서대로. updatedAt 은 마지막 제품 적재 시각.")
    @GetMapping("/rankings/categories")
    public ResponseEntity<CommonResponse<CategoriesResponse>> categories() {
        return ResponseEntity.ok(CommonResponse.success(rankingService.categories()));
    }

    @Operation(summary = "칩 순위 (SRCH_007)",
            description = "분석 완료 · 생산 중 제품을 goal 점수 높은 순(같으면 이름순)으로. sort 5가지(SCORE·POPULAR·CALORIES_LOW·PROTEIN_HIGH·SUGAR_LOW, 영양소는 100g·100mL당). " +
                    "rank 는 지금 정렬의 순번. coverage = 「000개 중 000개 점수 있음」. 열리지 않은 칩 404 RANKING_NOT_OPEN. goal 은 저장하지 않습니다." + FILTER_DOC)
    @GetMapping("/rankings")
    public ResponseEntity<CommonResponse<RankingResponse>> ranking(
            Actor actor,
            @Parameter(description = "칩 코드 (중분류 4자리 · 합산 칩)") @RequestParam(required = false) String category,
            @Parameter(description = "WEIGHT_LOSS | MUSCLE_GAIN") @RequestParam(required = false) String goal,
            @Parameter(description = "하루 칼로리 구간 (1500·2000·2500·3000)") @RequestParam(required = false) Integer kcalBand,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) List<String> brands,
            @RequestParam(required = false) List<String> nutrients,
            @RequestParam(required = false) List<String> sellers,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(rankingService.ranking(category, goal, kcalBand, sort,
                RankingService.filters(brands, nutrients, sellers), page, size, actor)));
    }

    @Operation(summary = "대분류 전체 순위",
            description = "열린 칩마다 필터에 맞는 상위 3개(점수 높은 순, 정렬 없음). 0개인 칩은 빼고, 열린 칩이 없으면 groups 빈 배열." + FILTER_DOC)
    @GetMapping("/rankings/overview")
    public ResponseEntity<CommonResponse<OverviewResponse>> overview(
            Actor actor,
            @Parameter(description = "대분류 코드 2자리") @RequestParam String major,
            @RequestParam(required = false) String goal,
            @RequestParam(required = false) Integer kcalBand,
            @RequestParam(required = false) List<String> brands,
            @RequestParam(required = false) List<String> nutrients,
            @RequestParam(required = false) List<String> sellers) {
        return ResponseEntity.ok(CommonResponse.success(rankingService.overview(major, goal, kcalBand,
                RankingService.filters(brands, nutrients, sellers), actor)));
    }

    @Operation(summary = "필터 시트 (SRCH_008)",
            description = "count = 지금 필터 조합의 제품 수(「000개 제품 보기」). nutrients 6칩은 그 분류에서 85% 초과 또는 10개 미만이면 hidden. " +
                    "brands.top = 많이 만드는 확인된 브랜드 8곳(2025 생산량 자료가 없어 지금은 제품 수 순), totalBrands = 「브랜드 더 보기 (n곳)」. category 는 칩 또는 대분류 코드." + FILTER_DOC)
    @GetMapping("/rankings/filters")
    public ResponseEntity<CommonResponse<FilterSheetResponse>> filters(
            @RequestParam String category,
            @RequestParam(required = false) List<String> brands,
            @RequestParam(required = false) List<String> nutrients,
            @RequestParam(required = false) List<String> sellers) {
        return ResponseEntity.ok(CommonResponse.success(rankingService.filters(category, RankingService.filters(brands, nutrients, sellers))));
    }

    @Operation(summary = "브랜드 더 보기",
            description = "그 칩(대분류 코드면 대분류 전체)의 브랜드 줄 전부(「제조 ○○」 포함), 가나다순, 제품 수. q 로 이름 포함 검색.")
    @GetMapping("/rankings/brands")
    public ResponseEntity<CommonResponse<BrandListResponse>> brands(
            @RequestParam String category,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(CommonResponse.success(rankingService.brands(category, q)));
    }
}
