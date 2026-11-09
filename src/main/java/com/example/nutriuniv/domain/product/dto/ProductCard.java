package com.example.nutriuniv.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * 목록 카드 한 장 (v3 SRCH_001 · SRCH_007 · RTN_001 · COMP_006) — 검색 · 랭킹 · 기록 · 즐겨찾기 · 더 맞는 제품이 같은 모양.
 * <ul>
 *   <li>goals: 두 목표의 점수 · 등급. 점수가 없는 제품은 빈 배열(카드는 「점수 없음」)</li>
 *   <li>tags: 요청한 목표 기준 「이 점수인 이유」 위 2개 짧은 말(좋은 점 1 + 아쉬운 점 1)</li>
 *   <li>sortValue: 영양소 정렬일 때만 — 카드 태그 한 줄을 「단백질 21g · 100g당」으로 바꾼다</li>
 *   <li>better: 그 목표에서 같은 중분류에 더 맞는 제품이 있는지 (기록 · 즐겨찾기 · 결과에서만)</li>
 * </ul>
 */
public record ProductCard(
        Long productId,
        String name,
        String brandLine,
        String imageUrl,
        String state,
        String stateLabel,
        List<GoalGrade> goals,
        List<String> tags,
        @JsonInclude(JsonInclude.Include.NON_NULL) SortValue sortValue,
        @JsonInclude(JsonInclude.Include.NON_NULL) BetterChoice better,
        boolean saved
) {

    public record GoalGrade(String goal, BigDecimal score, String grade, String label) {}

    public record SortValue(String nutrient, BigDecimal value, String unit, String basis, String text) {}

    /** TOP: 이미 A · B / AVAILABLE: 더 맞는 제품 count 개 / NONE: C~E 인데 후보 0개. 점수가 없으면 카드에 없다. */
    public record BetterChoice(String status, long count) {}

    public ProductCard withBetter(BetterChoice b) {
        return new ProductCard(productId, name, brandLine, imageUrl, state, stateLabel, goals, tags, sortValue, b, saved);
    }

    /** 그 목표의 점수 · 등급 (없으면 null). */
    public GoalGrade grade(String goal) {
        return goals.stream().filter(g -> g.goal().equals(goal)).findFirst().orElse(null);
    }
}
