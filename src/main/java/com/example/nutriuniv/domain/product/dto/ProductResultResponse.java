package com.example.nutriuniv.domain.product.dto;

import com.example.nutriuniv.domain.link.dto.PurchaseLinkResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 제품 결과 (v3 RSLT_002) — GET /products/{id} 와 스캔 결과가 같은 모양을 쓴다.
 * <ul>
 *   <li>goals: 두 목표(감량 · 근육 증가)를 한 번에. 점수가 없는 제품(NO_SCORE 등)이면 빈 배열</li>
 *   <li>nutrients: 성분표 순서. 값이 없으면 value = null · missing = true (0 과 구분)</li>
 *   <li>per100g 의 단계(level)가 좋은 점 · 아쉬운 점을 고른다. basis 가 「100mL」이면 100mL 기준</li>
 * </ul>
 */
public record ProductResultResponse(
        Long productId,
        String name,
        String brandLine,           // 브랜드, 없으면 「제조 ○○」
        String imageUrl,
        String state,               // NO_SCORE | ANALYZED | PRODUCTION_STOPPED | STOCK_REMAINING | DISCONTINUED
        String stateLabel,
        CategoryInfo category,      // 분류가 없으면 null
        Serving serving,            // 1회 제공량이 없으면 null
        String basis,               // 100g | 100mL
        int kcalBand,               // 감량 점수에 쓴 하루 칼로리 구간
        String gradeVersion,        // 기준선 버전 — 점수가 없으면 null
        List<GoalResult> goals,
        List<NutrientRow> nutrients,
        PurchaseLinkResponse purchase,
        boolean saved,
        String sourceNote
) {

    public record CategoryInfo(String code, String name) {}

    public record Serving(String text, BigDecimal amount, String unit) {}

    public record GoalResult(
            String goal,            // WEIGHT_LOSS | MUSCLE_GAIN
            String goalLabel,
            BigDecimal score,
            String grade,           // A~E
            String label,           // 잘 맞음 · 맞는 편 · 보통 · 안 맞는 편 · 안 맞음
            String summary,         // 한 줄 해석
            List<Point> goods,
            List<Point> bads,       // 아쉬운 점
            String badsEmptyText    // 아쉬운 점이 없을 때만 「이 목표에서 아쉬운 점이 없어요」
    ) {}

    public record Point(String nutrient, String text, boolean missing, String note) {}

    public record NutrientRow(
            String nutrient,        // CALORIES · CARBOHYDRATE · SUGAR · DIETARY_FIBER · PROTEIN · FAT · SATURATED_FAT · TRANS_FAT · CHOLESTEROL · SODIUM
            String label,
            String unit,
            Amount per100g,
            Amount perServing,      // 1회 제공량 값이 없으면 null
            boolean missing,        // 100g 값이 없음 → 화면은 「표시 없음」
            Map<String, String> tones   // 목표별 GOOD | BAD | NEUTRAL — 등급에 쓰는 8종만, 점수가 없으면 null
    ) {}

    public record Amount(BigDecimal value, Integer dvPercent, String level, String levelLabel) {}
}
