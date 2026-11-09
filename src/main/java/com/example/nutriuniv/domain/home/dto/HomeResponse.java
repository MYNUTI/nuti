package com.example.nutriuniv.domain.home.dto;

import com.example.nutriuniv.domain.ranking.dto.RankingDtos;

import java.util.List;
import java.util.Map;

/**
 * GET /home (v3 SRCH_006) — 홈 한 화면.
 * <ul>
 *   <li>viewedCount: 최근 본 제품 수(제품 기준). 소유자가 없으면 0</li>
 *   <li>goals: 본 제품의 목표별 등급 분포(A~E · NONE)와 A+B 개수</li>
 *   <li>requestCount: 내가 넣은 분석 요청 수</li>
 *   <li>majors: 순위가 열린 칩이 있는 대분류만</li>
 *   <li>sellers: 판매처별 순위 대상 제품 수 (자료가 없는 판매처는 0 · available=false)</li>
 * </ul>
 */
public record HomeResponse(
        String goal,
        int kcalBand,
        long viewedCount,
        List<GoalDistribution> goals,
        long requestCount,
        List<RankingDtos.Major> majors,
        List<RankingDtos.SellerCount> sellers
) {

    public record GoalDistribution(String goal, String goalLabel, Map<String, Long> grades, long goodCount) {}
}
