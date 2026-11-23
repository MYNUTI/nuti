package com.example.nutriuniv.domain.home.dto;

import com.example.nutriuniv.domain.ranking.dto.RankingDtos;

import java.util.List;

/**
 * GET /home (v3 SRCH_006) — 홈 한 화면.
 * <ul>
 *   <li>viewedCount: 최근 본 제품 수(제품 기준). 소유자가 없으면 0 — 0이면 첫 방문 구성</li>
 *   <li>requestCount: 내가 넣은 분석 요청 수 (0이면 알약을 숨긴다)</li>
 *   <li>majors: 대분류 15개 전부(칩은 순위가 열린 것만, 비어 있을 수 있음)</li>
 *   <li>sellers: 판매처별 순위 대상 제품 수 (자료가 없는 판매처는 0 · available=false)</li>
 * </ul>
 */
public record HomeResponse(
        long viewedCount,
        long requestCount,
        List<RankingDtos.Major> majors,
        List<RankingDtos.SellerCount> sellers
) {
}
