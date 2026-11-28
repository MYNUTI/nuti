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
 * 점수 식 (v3 CORE_001 「점수 식」 · 11-18 확정). 값은 100g당, 성분 기준 단위가 100mL 인 제품은 100mL당.
 * <pre>
 *  원점수 = 단백질 가점 − (당류 + 포화지방 + 나트륨 + 트랜스지방 + 콜레스테롤 감점) + 목표 항
 *    단백질 가점      clip(단백질 ÷ 11)                         · 100mL: ÷ 5.5
 *    당류 감점        1 − 0.5^(max(당류 − 5, 0) ÷ 20)            · 100mL: − 2.5, ÷ 10
 *    포화지방 감점    clip((포화지방 − 1.5) ÷ 13.5)              · 100mL: − 0.75, ÷ 14.25
 *    나트륨 감점      clip((나트륨 − 120) ÷ 1880)
 *    트랜스지방 감점  clip((트랜스지방 − 0.2) ÷ 1.8)
 *    콜레스테롤 감점  0.25 × clip((콜레스테롤 − 20) ÷ 280)       · 100mL: − 10, ÷ 290
 *  목표 항
 *    다이어트   − clip((칼로리 − 40) ÷ (끝 − 40)) + 식이섬유 가점(0.05 · 0.1)   · 100mL: 40 → 20
 *               끝 = 칼로리 구간 1,500 → 400 · 2,000 → 627 · 2,500 → 855 · 3,000 → 900
 *    근육 증가  + 0.5 × 칼로리 비율 × 단백질 비율
 *               칼로리 비율 = clip((칼로리 − 4 × 당류 − 9 × 포화지방 − 40) ÷ 360)   · 100mL: − 20, ÷ 380
 *               단백질 비율 = clip((100kcal당 단백질 − 2.75) ÷ 2.75)
 *  점수 = round1(clamp(100 × (원점수 − 아래 끝) ÷ (위 끝 − 아래 끝), 0, 100)) — 등급은 반올림한 점수로 가른다
 * </pre>
 * 위 끝은 식의 최댓값(다이어트 1.1 · 근육 증가 1.5)으로 고정, 아래 끝(원점수 1% 분위)과 등급 경계는 기준선 버전에 있다(CORE_007).
 * 순수 함수 — DB·스프링 의존 없음.
 */
public final class GradeFormula {

    private GradeFormula() {}

    /** 위 끝 — 식에서 나올 수 있는 가장 높은 원점수 (다이어트 = 단백질 1 + 식이섬유 0.1, 근육 증가 = 단백질 1 + 칼로리 0.5). */
    public static double maxRaw(GoalType goal) {
        return goal == GoalType.MUSCLE_GAIN ? 1.5 : 1.1;
    }

    /**
     * 100g(또는 100mL) 기준 입력 8종. 식이섬유는 없으면 0 으로 넘긴다(가점이 없을 뿐 감점은 없다).
     * perMl 이면 성분 기준 단위가 100mL 인 제품 — 100mL 기준선을 쓴다.
     */
    public record Input(double calories, double protein, double fiber, double sugar,
                        double saturatedFat, double transFat, double cholesterol, double sodium, boolean perMl) {

        public Input(double calories, double protein, double fiber, double sugar,
                     double saturatedFat, double transFat, double cholesterol, double sodium) {
            this(calories, protein, fiber, sugar, saturatedFat, transFat, cholesterol, sodium, false);
        }

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

        /** 가장 큰 감점 요인(가장 음수인 기여). 감점이 없으면 null. 칼로리는 다이어트에서만 감점이 된다. */
        public Nutrient topPenalty() {
            Contribution worst = null;
            for (Contribution c : contributions) {
                if (c.value() < 0 && (worst == null || c.value() < worst.value())) {
                    worst = c;
                }
            }
            return worst == null ? null : worst.nutrient();
        }

        /** 근육 증가 칼로리 가점이 붙었는지 (CORE_004 칼로리 좋은 점). */
        public boolean calorieBonus() {
            for (Contribution c : contributions) {
                if (c.nutrient() == Nutrient.CALORIES && c.value() > 0) return true;
            }
            return false;
        }
    }

    public record Result(double raw, double score, Grade grade, Nutrient topPenaltyNutrient) {}

    // ── 계산 ──────────────────────────────────────────────────────────────────

    /** 원점수. band 는 칼로리 구간 슬롯(1500 · 2000 · 2500 · 3000) — 다이어트 칼로리 감점의 끝을 정한다. */
    public static Raw raw(GoalType goal, int band, Input in) {
        boolean ml = in.perMl();
        double kcal = pos(in.calories());
        double protein = pos(in.protein());
        double sugar = pos(in.sugar());
        double sat = pos(in.saturatedFat());
        double fiber = pos(in.fiber());

        List<Contribution> parts = new ArrayList<>(8);
        parts.add(new Contribution(Nutrient.PROTEIN, CriterionDirection.BONUS, clip(protein / (ml ? 5.5 : 11.0))));

        double overSugar = Math.max(0.0, sugar - (ml ? 2.5 : 5.0));
        parts.add(new Contribution(Nutrient.SUGAR, CriterionDirection.PENALTY, -(1.0 - Math.pow(0.5, overSugar / (ml ? 10.0 : 20.0)))));
        parts.add(new Contribution(Nutrient.SATURATED_FAT, CriterionDirection.PENALTY,
                -clip((sat - (ml ? 0.75 : 1.5)) / (ml ? 14.25 : 13.5))));
        parts.add(new Contribution(Nutrient.SODIUM, CriterionDirection.PENALTY, -clip((pos(in.sodium()) - 120.0) / 1880.0)));
        parts.add(new Contribution(Nutrient.TRANS_FAT, CriterionDirection.PENALTY, -clip((pos(in.transFat()) - 0.2) / 1.8)));
        parts.add(new Contribution(Nutrient.CHOLESTEROL, CriterionDirection.PENALTY,
                -0.25 * clip((pos(in.cholesterol()) - (ml ? 10.0 : 20.0)) / (ml ? 290.0 : 280.0))));

        if (goal == GoalType.MUSCLE_GAIN) {
            double base = ml ? 20.0 : 40.0;
            double calRatio = clip((kcal - 4.0 * sugar - 9.0 * sat - base) / (ml ? 380.0 : 360.0));
            double proteinPer100kcal = kcal > 0 ? protein / kcal * 100.0 : 0.0;
            double proteinRatio = clip((proteinPer100kcal - 2.75) / 2.75);
            parts.add(new Contribution(Nutrient.CALORIES, CriterionDirection.CALORIE, 0.5 * calRatio * proteinRatio));
        } else {
            double base = ml ? 20.0 : 40.0;
            double end = calorieEnd(band);
            parts.add(new Contribution(Nutrient.CALORIES, CriterionDirection.CALORIE, -clip((kcal - base) / (end - base))));
            parts.add(new Contribution(Nutrient.DIETARY_FIBER, CriterionDirection.BONUS, fiberBonus(fiber, kcal)));
        }

        double sum = 0.0;
        for (Contribution c : parts) sum += c.value();
        return new Raw(sum, List.copyOf(parts));
    }

    /** 다이어트 식이섬유 가점: 100g(mL)당 6g 이상이거나 100kcal당 3g 이상 +0.1, 3g · 1.5g 이상 +0.05. 칼로리 0 이면 100kcal당은 보지 않는다. */
    static double fiberBonus(double fiber, double kcal) {
        double per100kcal = kcal > 0 ? fiber / kcal * 100.0 : -1.0;
        if (fiber >= 6.0 || per100kcal >= 3.0) return 0.1;
        if (fiber >= 3.0 || per100kcal >= 1.5) return 0.05;
        return 0.0;
    }

    /** 다이어트 칼로리 감점이 1 이 되는 칼로리 — 칼로리 구간별(100mL 표시도 같은 값). 4종 밖이면 2,000kcal 값. */
    public static double calorieEnd(int band) {
        return switch (band) {
            case 1500 -> 400.0;
            case 2500 -> 855.0;
            case 3000 -> 900.0;
            default -> 627.0;
        };
    }

    /** 0~100 정규화, 소수 1자리(둘째 자리에서 반올림 · 5는 올림). anchor.p1 = 아래 끝, anchor.p99 = 위 끝. */
    public static double normalize(double raw, Calibration.Anchor anchor) {
        double span = anchor.p99() - anchor.p1();
        if (span <= 0.0) return raw >= anchor.p99() ? 100.0 : 0.0;
        return round1(clamp((raw - anchor.p1()) / span * 100.0, 0.0, 100.0));
    }

    /** 원점수부터 등급까지 한 번에. */
    public static Result evaluate(Calibration cal, GoalType goal, int band, Input in) {
        Raw raw = raw(goal, band, in);
        double score = normalize(raw.raw(), cal.anchor(goal));
        return new Result(raw.raw(), score, cal.gradeOf(goal, score), raw.topPenalty());
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────

    static double clip(double x) {
        if (Double.isNaN(x)) return 0.0;
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

    /** 음수 · NaN · 무한대는 0. */
    private static double pos(double d) {
        return (Double.isNaN(d) || Double.isInfinite(d)) ? 0.0 : Math.max(0.0, d);
    }
}
