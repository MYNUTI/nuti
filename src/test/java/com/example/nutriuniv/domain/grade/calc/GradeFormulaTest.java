package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CORE_001 「점수 식」(11-18) — 명세의 확인용 값 「가. 제품 예 6개」와 식의 경계를 고정한다.
 * 맞춰 볼 것은 점수(소수 첫째 자리)와 등급, 원점수는 소수 셋째 자리 참고값.
 */
class GradeFormulaTest {

    private static final Calibration BASE = DefaultCalibration.snapshot();

    private static GradeFormula.Input in(double cal, double prot, double fib, double sug,
                                         double sat, double trans, double chol, double na, boolean ml) {
        return new GradeFormula.Input(cal, prot, fib, sug, sat, trans, chol, na, ml);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // 이름, 칼로리, 단백질, 식이섬유, 당류, 포화지방, 트랜스지방, 콜레스테롤, 나트륨, mL, 다이어트 원점수·점수·등급, 근육 증가 원점수·점수·등급
            "닭가슴살,     110, 23,   0, 0,   0.5, 0,   60,  300, false,  0.749, 89.6, A,  0.960, 81.7, A",
            "삶은 달걀,    145, 12.5, 0, 0.7, 3.1, 0,   370, 130, false,  0.447, 80.6, A,  0.729, 73.9, A",
            "그래놀라,     430, 9,    7, 22,  3,   0,   0,   150, false, -0.318, 57.8, C,  0.246, 57.6, B",
            "쿠키,         500, 6,    2, 30,  12,  0.3, 30,  350, false, -1.782, 14.2, E, -0.999, 15.5, E",
            "콜라,         45,  0,    0, 11,  0,   0,   0,   5,   true,  -0.486, 52.8, D, -0.445, 34.2, D",
            "단백질 음료,  60,  8,    0, 2,   0.4, 0,   5,   60,  true,   0.934, 95.1, A,  1.037, 84.3, A",
    })
    void 명세_확인용_제품_6개(String name, double cal, double prot, double fib, double sug, double sat, double trans,
                       double chol, double na, boolean ml,
                       double wlRaw, double wlScore, Grade wlGrade, double mgRaw, double mgScore, Grade mgGrade) {
        GradeFormula.Input input = in(cal, prot, fib, sug, sat, trans, chol, na, ml);

        GradeFormula.Result wl = GradeFormula.evaluate(BASE, GoalType.WEIGHT_LOSS, 2000, input);
        assertEquals(wlRaw, wl.raw(), 0.0006, name + " 다이어트 원점수");
        assertEquals(wlScore, wl.score(), name + " 다이어트 점수");
        assertEquals(wlGrade, wl.grade(), name + " 다이어트 등급");

        GradeFormula.Result mg = GradeFormula.evaluate(BASE, GoalType.MUSCLE_GAIN, 2000, input);
        assertEquals(mgRaw, mg.raw(), 0.0006, name + " 근육 증가 원점수");
        assertEquals(mgScore, mg.score(), name + " 근육 증가 점수");
        assertEquals(mgGrade, mg.grade(), name + " 근육 증가 등급");
    }

    @Test
    void 식이섬유_가점은_다이어트에만_있다() {
        GradeFormula.Input fiber = in(200, 0, 6, 0, 0, 0, 0, 0, false);
        GradeFormula.Input none = in(200, 0, 0, 0, 0, 0, 0, 0, false);
        assertEquals(0.1, GradeFormula.raw(GoalType.WEIGHT_LOSS, 2000, fiber).raw()
                - GradeFormula.raw(GoalType.WEIGHT_LOSS, 2000, none).raw(), 1e-9);
        assertEquals(GradeFormula.raw(GoalType.MUSCLE_GAIN, 2000, none).raw(),
                GradeFormula.raw(GoalType.MUSCLE_GAIN, 2000, fiber).raw(), 1e-9);
    }

    @Test
    void 식이섬유_가점_단계() {
        assertEquals(0.0, GradeFormula.fiberBonus(2.9, 300), 1e-9);
        assertEquals(0.05, GradeFormula.fiberBonus(3.0, 300), 1e-9);    // 100g당 3g
        assertEquals(0.05, GradeFormula.fiberBonus(1.5, 100), 1e-9);    // 100kcal당 1.5g
        assertEquals(0.1, GradeFormula.fiberBonus(6.0, 900), 1e-9);     // 100g당 6g
        assertEquals(0.1, GradeFormula.fiberBonus(3.0, 100), 1e-9);     // 100kcal당 3g
        assertEquals(0.0, GradeFormula.fiberBonus(1.0, 0), 1e-9);       // 칼로리 0 이면 100kcal당은 보지 않는다
    }

    @Test
    void 근육_증가_칼로리_가점은_단백질이_충분할_때만() {
        // 단백질 0 → 단백질 비율 0 → 칼로리가 높아도 가점 없음
        GradeFormula.Raw noProtein = GradeFormula.raw(GoalType.MUSCLE_GAIN, 2000, in(500, 0, 0, 0, 0, 0, 0, 0, false));
        assertFalse(noProtein.calorieBonus());
        // 400kcal, 단백질 22g(100kcal당 5.5g → 비율 1), 칼로리 비율 clip((400 − 40)/360) = 1 → 0.5
        GradeFormula.Raw rich = GradeFormula.raw(GoalType.MUSCLE_GAIN, 2000, in(400, 22, 0, 0, 0, 0, 0, 0, false));
        assertTrue(rich.calorieBonus());
        assertEquals(1.5, rich.raw(), 1e-9);
    }

    @Test
    void 다이어트_칼로리_감점의_끝은_칼로리_구간마다_다르다() {
        GradeFormula.Input input = in(500, 0, 0, 0, 0, 0, 0, 0, false);
        assertEquals(-1.0, GradeFormula.raw(GoalType.WEIGHT_LOSS, 1500, input).raw(), 1e-9);              // 끝 400
        assertEquals(-460.0 / 587.0, GradeFormula.raw(GoalType.WEIGHT_LOSS, 2000, input).raw(), 1e-9);  // 끝 627
        assertEquals(-460.0 / 815.0, GradeFormula.raw(GoalType.WEIGHT_LOSS, 2500, input).raw(), 1e-9);  // 끝 855
        assertEquals(-460.0 / 860.0, GradeFormula.raw(GoalType.WEIGHT_LOSS, 3000, input).raw(), 1e-9);  // 끝 900
    }

    @Test
    void 가장_큰_감점_요인() {
        GradeFormula.Raw r = GradeFormula.raw(GoalType.WEIGHT_LOSS, 2000, in(100, 0, 0, 0, 0, 0, 0, 2000, false));
        assertEquals(Nutrient.SODIUM, r.topPenalty());
        assertNull(GradeFormula.raw(GoalType.MUSCLE_GAIN, 2000, in(0, 11, 0, 0, 0, 0, 0, 0, false)).topPenalty());
    }

    @Test
    void 점수는_0과_100에서_잘리고_경계_점수는_그_등급이다() {
        Calibration.Anchor a = BASE.anchor(GoalType.WEIGHT_LOSS);
        assertEquals(0.0, GradeFormula.normalize(-9.0, a));
        assertEquals(100.0, GradeFormula.normalize(1.1, a));
        assertEquals(Grade.A, BASE.gradeOf(GoalType.WEIGHT_LOSS, 72.7));
        assertEquals(Grade.B, BASE.gradeOf(GoalType.WEIGHT_LOSS, 72.6));
        assertEquals(Grade.D, BASE.gradeOf(GoalType.WEIGHT_LOSS, 33.3));
        assertEquals(Grade.E, BASE.gradeOf(GoalType.WEIGHT_LOSS, 33.2));
        assertEquals(Grade.A, BASE.gradeOf(GoalType.MUSCLE_GAIN, 67.4));
        assertEquals(Grade.E, BASE.gradeOf(GoalType.MUSCLE_GAIN, 29.6));
        assertEquals("매우 높음", BASE.label(GoalType.WEIGHT_LOSS, Grade.A));
        assertEquals("매우 낮음", BASE.label(GoalType.MUSCLE_GAIN, Grade.E));
    }
}
