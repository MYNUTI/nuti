package com.example.nutriuniv.domain.ranking.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.AdminChip;
import com.example.nutriuniv.domain.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Ranking", description = "칩별 순위 공개 현황")
@RestController
@RequiredArgsConstructor
public class AdminRankingController {

    private final RankingService rankingService;

    @Operation(summary = "칩별 현황", description = "모든 칩(닫힌 칩 포함)의 제품 수 · 분석 완료 수 · 공개 여부와 닫힌 이유.")
    @GetMapping("/admin/rankings/categories")
    public ResponseEntity<CommonResponse<List<AdminChip>>> categories() {
        return ResponseEntity.ok(CommonResponse.success(rankingService.adminCategories()));
    }
}
