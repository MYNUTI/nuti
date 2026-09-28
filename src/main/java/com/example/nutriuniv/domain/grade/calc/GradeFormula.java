package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.CriterionDirection;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.Nutrient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 등급 산식 (기능명세서 4.1) — 구 PnsCalculator v4.1 과 같은 계산을 기준값 표(Calibration) 위에서 한다.
 * <pre>
 *  ① 입력은 100g 기준 8종 (호출부가 통일)
 *  ② 유익 가점  BONUS  : +w · min(x / 만점선, 1)
 *     제한 감점  PENALTY: −w · clip((x − 시작값) / (최대값 − 시작값))
 *  ③ 열량 부호  CALORIE: w · ED,  ED = x ≤ 0점 ? 0 : clip((x − 0점) / (M − 0점)),  w = 감량 −1 · 근육 +1 · 일반 0
 *     raw = 합
 *  ④ score = round1(clamp((raw − P1) / (P99 − P1) × 100, 0, 100))
 *  ⑤ grade = 컷오프 (score ≥ min_score 인 가장 높은 등급)
 * </pre>
 * 순수 함수 — DB·스프링 의존 없음. 테스트로 v1 기본값이 구 산식과 같은 값을 내는 것을 고정한다.
 */
public final class GradeFormula {

    private GradeFormula() {}

    /** 100g 기준 입력 8종. 식이섬유는 없으면 0 으로 넘긴다. */
    public record Input(double calories, double protein, double fiber, double sugar,
                        double saturatedFat, double transFat, double cholesterol, double sodium) {

        public double of(Nutrient n) {
            return switch (n) {
                case CALORIES      -> calories;
                case PROTEIN       -> protein;
                case DIETARY_FIBER -> fiber;
                case SUGAR         -> sugar;
                case SATURATED_FAT -> saturatedFat;
                case TRANS_FAT     -> transFat;
                case CHOLESTEROL   -> cholesterol;
                case SODIUM        -> sodium;
            };
        }
    }

    /** 성분 하나의 기여값 (가점 +, 감점 −). */
    public record Contribution(Nutrient nutrient, CriterionDirection direction, double value) {}

    public record Raw(double raw, List<Contribution> contributions) {

        /** 가장 큰 감점 요인(가장 음수인 기여). 감점이 없으면 null. 열량은 감량 목표에서만 감점이 된다. */
        public Nutrient topPenalty() {
            Contribution worst = null;
            for (Contribution c : contributions) {
                if (c.value() < 0 && (worst == null || c.value() < worst.value())) {
                    worst = c;
                }
            }
            return worst == null ? null : worst.nutrient();
        }
    }

    public record Result(double raw, double score, Grade grade, Nutrient topPenaltyNutrient) {}

    // ── 계산 ──────────────────────────────────────────────────────────────────

    /** ②·③ — 정규화 전 raw 점수. band 는 슬롯 값(감량·근육 1500~3000, 일반 0). */
    public static Raw raw(GoalType goal, int band, Input in, List<Calibration.Rule> rules) {
        List<Contribution> parts = new ArrayList<>(rules.size());
        double sum = 0.0;
        for (Calibration.Rule r : rules) {
            double x = Math.max(0.0, sanitize(in.of(r.nutrient())));
            double v = switch (r.direction()) {
                case BONUS -> r.threshold() <= 0.0 ? 0.0 : r.weight() * Math.min(x / r.threshold(), 1.0);
                case PENALTY -> {
                    double hi = r.maxValue() == null ? r.threshold() : r.maxValue();
                    double ratio = hi <= r.threshold() ? (x > r.threshold() ? 1.0 : 0.0) : clip((x - r.threshold()) / (hi - r.threshold()));
                    yield -r.weight() * ratio;
                }
                case CALORIE -> {
                    double m = r.maxValue() == null ? EerBand.energyDensityCap(band) : r.maxValue();
                    double ed = x <= r.threshold() || m <= r.threshold() ? 0.0 : clip((x - r.threshold()) / (m - r.threshold()));
                    yield r.weight() * ed;
                }
            };
            parts.add(new Contribution(r.nutrient(), r.direction(), v));
            sum += v;
        }
        return new Raw(sum, List.copyOf(parts));
    }

    /** ④ — 0~100 정규화, 소수 1자리. */
    public static double normalize(double raw, Calibration.Anchor anchor) {
        double span = anchor.p99() - anchor.p1();
        if (span <= 0.0) return raw >= anchor.p99() ? 100.0 : 0.0;
        return round1(clamp((raw - anchor.p1()) / span * 100.0, 0.0, 100.0));
    }

    /** ②~⑤ 한 번에. */
    public static Result evaluate(Calibration cal, GoalType goal, int band, Input in) {
        Raw raw = raw(goal, band, in, cal.rules(goal));
        double score = normalize(raw.raw(), cal.anchor(goal));
        return new Result(raw.raw(), score, cal.gradeOf(goal, score), raw.topPenalty());
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    static double clip(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    static double clamp(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

    public static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static double sanitize(double d) {
        return (Double.isNaN(d) || Double.isInfinite(d)) ? 0.0 : d;
    }
}
