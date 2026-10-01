package com.example.nutriuniv.domain.ranking.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.ranking.dto.RankingCategoriesResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingCriteriaResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingResponse;
import com.example.nutriuniv.domain.ranking.service.RankingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ranking", description = "분류별 랭킹 (기능명세서 6.3, 2차) — 매일 04:00 배치 결과를 읽기만 합니다")
@RestController
@RequiredArgsConstructor
public class RankingController {

    private final RankingQueryService rankingQueryService;

    // GET /rankings/categories
    @Operation(summary = "랭킹 분류 목록 (명세 6.3)",
            description = "게이트(분석 완료 300건 이상 + A·D 각 1건 이상, 일반 기준)를 통과한 서비스 분류만. " +
                    "서비스 분류 체계가 아직 정해지지 않았거나 배치 전이면 빈 목록 — 클라이언트는 랭킹 탭을 숨깁니다. isDefault 는 정확히 하나.")
    @GetMapping("/rankings/categories")
    public ResponseEntity<CommonResponse<RankingCategoriesResponse>> categories() {
        return ResponseEntity.ok(CommonResponse.success(rankingQueryService.categories()));
    }

    // GET /rankings
    @Operation(summary = "분류 랭킹 (명세 6.3)",
            description = "등급순(동점은 분석 최신순). 순위와 등급을 함께 줍니다 — 「1위인데 D등급」이 실제로 나옵니다. " +
                    "goal 생략 시 내 목표(없으면 GENERAL). 영양정보 부족 제품은 순위에서 빼고 coverage 분모에는 포함. " +
                    "없는 분류 404 RESOURCE_NOT_FOUND, 게이트 미통과 분류 404 RANKING_NOT_OPEN, page 음수·size 1~50 밖 400.")
    @GetMapping("/rankings")
    public ResponseEntity<CommonResponse<RankingResponse>> rankings(
            Actor actor,
            @Parameter(description = "랭킹 분류 ID (/rankings/categories 의 categoryId)") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "GENERAL | WEIGHT_LOSS | MUSCLE_GAIN (선택)") @RequestParam(required = false) String goal,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~50)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(rankingQueryService.rankings(categoryId, goal, page, size, actor)));
    }

    // GET /rankings/criteria
    @Operation(summary = "선정 기준 안내",
            description = "목록 위 [선정 기준] 링크 문구 — 정렬 기준·분류 열림 조건·분석 완료 비율 뜻·광고 미반영·갱신 주기. " +
                    "DB(grade_copies RANKING_*)에서 읽어 산식 변경 시 클라 배포 없이 갱신. categoryId 를 주면 그 분류의 현황 문장을 맨 앞에 붙입니다.")
    @GetMapping("/rankings/criteria")
    public ResponseEntity<CommonResponse<RankingCriteriaResponse>> criteria(
            @Parameter(description = "랭킹 분류 ID (선택)") @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(CommonResponse.success(rankingQueryService.criteria(categoryId)));
    }
}
