package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v1 기본 기준(구 PnsCalculator v4.1 상수)이 구 산식과 같은 점수·등급을 내는지 고정한다.
 * 기대값은 구 산식을 손으로 계산한 값 — 앵커·컷오프·만점선·감점 구간·에너지밀도 M 을 모두 거친다.
 */
class GradeFormulaTest {

    private static final Calibration V1 = DefaultCalibration.snapshot();
    private static final int WL_SLOT = EerBand.defaultSlot(GoalType.WEIGHT_LOSS);   // 2000
    private static final int MG_SLOT = EerBand.defaultSlot(GoalType.MUSCLE_GAIN);   // 2000
    private static final int GEN_SLOT = EerBand.defaultSlot(GoalType.GENERAL);      // 0

    private static GradeFormula.Input input(double cal, double prot, double fib, double sug,
                                            double sat, double trans, double chol, double na) {
        return new GradeFormula.Input(cal, prot, fib, sug, sat, trans, chol, na);
    }

    @Test
    void 전부_0인_제품_raw_0_목표별_점수와_등급() {
        GradeFormula.Input zero = input(0, 0, 0, 0, 0, 0, 0, 0);

        // 감량: (0 + 1.76) / 3.11 × 100 = 56.59 → 56.6 → D (57.8 미만, 45.5 이상)
        GradeFormula.Result wl = GradeFormula.evaluate(V1, GoalType.WEIGHT_LOSS, WL_SLOT, zero);
        assertEquals(0.0, wl.raw(), 1e-12);
        assertEquals(56.6, wl.score());
        assertEquals(Grade.D, wl.grade());
        assertNull(wl.topPenaltyNutrient());

        // 일반(구 health): (0 + 1.07) / 2.88 × 100 = 37.15 → 37.2 → D (37.1 이상)
        GradeFormula.Result gen = GradeFormula.evaluate(V1, GoalType.GENERAL, GEN_SLOT, zero);
        assertEquals(37.2, gen.score());
        assertEquals(Grade.D, gen.grade());

        // 근육 증가: (0 + 0.60) / 3.29 × 100 = 18.24 → 18.2 → E (24.7 미만)
        GradeFormula.Result mg = GradeFormula.evaluate(V1, GoalType.MUSCLE_GAIN, MG_SLOT, zero);
        assertEquals(18.2, mg.score());
        assertEquals(Grade.E, mg.grade());
    }

    @Test
    void 유익_만점_제품은_100점_A() {
        // 단백질 22g(만점선 11 초과 → 1), 식이섬유 6g(만점선 → 1): B = 2, raw = 2 → 정규화 120.9 → clamp 100
        GradeFormula.Input in = input(0, 22, 6, 0, 0, 0, 0, 0);
        GradeFormula.Result wl = GradeFormula.evaluate(V1, GoalType.WEIGHT_LOSS, WL_SLOT, in);
        assertEquals(2.0, wl.raw(), 1e-12);
        assertEquals(100.0, wl.score());
        assertEquals(Grade.A, wl.grade());
        assertNull(wl.topPenaltyNutrient());
    }

    @Test
    void 열량_부호_반전_감량은_감점_근육증가는_가점() {
        // 열량 500kcal/100g, 당류 50g. 당류 감점 = (50−5)/95 = 0.47368
        GradeFormula.Input in = input(500, 0, 0, 50, 0, 0, 0, 0);

        // 감량 @2000: M = 400 + 500×(500/1100) = 627.27, ED = (500−40)/(627.27−40) = 0.78328
        //   raw = −0.47368 − 0.78328 = −1.25697 → (−1.25697 + 1.76)/3.11 × 100 = 16.17 → 16.2 → E, 최대 감점 = 열량
        GradeFormula.Result wl = GradeFormula.evaluate(V1, GoalType.WEIGHT_LOSS, WL_SLOT, in);
        assertEquals(-1.25697, wl.raw(), 1e-4);
        assertEquals(16.2, wl.score());
        assertEquals(Grade.E, wl.grade());
        assertEquals(Nutrient.CALORIES, wl.topPenaltyNutrient());

        // 근육 증가: M = 400 고정, ED = clip(460/360) = 1 → raw = −0.47368 + 1 = 0.52632
        //   (0.52632 + 0.60)/3.29 × 100 = 34.23 → 34.2 → D, 최대 감점 = 당류 (열량은 가점)
        GradeFormula.Result mg = GradeFormula.evaluate(V1, GoalType.MUSCLE_GAIN, MG_SLOT, in);
        assertEquals(0.52632, mg.raw(), 1e-4);
        assertEquals(34.2, mg.score());
        assertEquals(Grade.D, mg.grade());
        assertEquals(Nutrient.SUGAR, mg.topPenaltyNutrient());

        // 일반: 열량 가중치 0 → raw = −0.47368 (구 health = core)
        GradeFormula.Result gen = GradeFormula.evaluate(V1, GoalType.GENERAL, GEN_SLOT, in);
        assertEquals(-0.47368, gen.raw(), 1e-4);
        assertEquals(Nutrient.SUGAR, gen.topPenaltyNutrient());
    }

    @Test
    void 감량_에너지밀도_상한은_열량구간에_따라_다르다() {
        GradeFormula.Input in = input(500, 0, 0, 0, 0, 0, 0, 0);
        List<Calibration.Rule> rules = V1.rules(GoalType.WEIGHT_LOSS);
        // 1500: M=400 → ED = clip(460/360) = 1 → raw −1 ; 3000: M=900 → ED = 460/860 = 0.5349 → raw −0.5349
        assertEquals(-1.0, GradeFormula.raw(GoalType.WEIGHT_LOSS, 1500, in, rules).raw(), 1e-9);
        assertEquals(-0.5349, GradeFormula.raw(GoalType.WEIGHT_LOSS, 3000, in, rules).raw(), 1e-4);
    }

    @Test
    void 컷오프_경계값은_그_등급이다() {
        assertEquals(Grade.A, V1.gradeOf(GoalType.WEIGHT_LOSS, 74.3));
        assertEquals(Grade.B, V1.gradeOf(GoalType.WEIGHT_LOSS, 74.2));
        assertEquals(Grade.D, V1.gradeOf(GoalType.WEIGHT_LOSS, 45.5));
        assertEquals(Grade.E, V1.gradeOf(GoalType.WEIGHT_LOSS, 45.4));
        assertEquals(Grade.E, V1.gradeOf(GoalType.WEIGHT_LOSS, 0.0));
        assertEquals("아주 잘 맞아요", V1.label(GoalType.GENERAL, Grade.A));
        assertEquals("잘 안 맞아요", V1.label(GoalType.MUSCLE_GAIN, Grade.E));
    }

    @Test
    void 정규화_앵커_P1은_0점_P99는_100점() {
        Calibration.Anchor a = new Calibration.Anchor(-1.76, 1.35);
        assertEquals(0.0, GradeFormula.normalize(-1.76, a));
        assertEquals(100.0, GradeFormula.normalize(1.35, a));
        assertEquals(0.0, GradeFormula.normalize(-5.0, a));      // 아래로 클램프
        assertEquals(100.0, GradeFormula.normalize(9.0, a));     // 위로 클램프
    }
}
