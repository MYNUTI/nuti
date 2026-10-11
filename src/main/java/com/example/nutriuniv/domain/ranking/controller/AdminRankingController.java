package com.example.nutriuniv.domain.ranking.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.ranking.dto.AdminRankingCategoriesResponse;
import com.example.nutriuniv.domain.ranking.dto.AdminRankingRebuildResponse;
import com.example.nutriuniv.domain.ranking.service.RankingBatchService;
import com.example.nutriuniv.domain.ranking.service.RankingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Ranking", description = "랭킹 배치 운영 — 서비스 분류·매핑을 넣은 뒤 04:00 을 기다리지 않고 채우거나, 어느 분류가 왜 안 열리는지 본다")
@RestController
@RequiredArgsConstructor
public class AdminRankingController {

    private final RankingBatchService rankingBatchService;
    private final RankingQueryService rankingQueryService;

    // GET /admin/rankings/categories
    @Operation(summary = "서비스 분류 현황",
            description = "활성 서비스 분류 전부(게이트 미통과 포함) — 매핑 수·분모·분석 완료·A/D·게이트 통과 여부와 미통과 이유·저장된 순위 수·마지막 배치 시각.")
    @GetMapping("/admin/rankings/categories")
    public ResponseEntity<CommonResponse<AdminRankingCategoriesResponse>> categories() {
        return ResponseEntity.ok(CommonResponse.success(rankingQueryService.adminCategories()));
    }

    // POST /admin/rankings/rebuild
    @Operation(summary = "랭킹 즉시 재계산",
            description = "새벽 배치와 같은 계산을 지금 실행합니다(동기, 수 초~수십 초). 매핑 추가·등급 재산출 직후에 씁니다. 진행 중이면 409.")
    @PostMapping("/admin/rankings/rebuild")
    public ResponseEntity<CommonResponse<AdminRankingRebuildResponse>> rebuild() {
        return ResponseEntity.ok(CommonResponse.success(AdminRankingRebuildResponse.from(rankingBatchService.rebuild("admin"))));
    }
}
