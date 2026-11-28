package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Evaluation;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Fact;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.FiberClaim;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Level;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Point;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Subject;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Tag;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Tone;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** CORE_004 좋은 점 · 아쉬운 점 · 평가 말, SRCH_001 카드 태그. */
class NutrientEvaluatorTest {

    private static final double TRANS = 0.2;

    private static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    /** 칼로리 · 단백질 · 식이섬유 · 당류 · 포화지방 · 트랜스지방 · 콜레스테롤 · 나트륨. null 이면 넣지 않는다. */
    private static Map<Nutrient, BigDecimal> per100g(String cal, String prot, String fib, String sug,
                                                     String sat, String trans, String chol, String na) {
        Map<Nutrient, BigDecimal> m = new EnumMap<>(Nutrient.class);
        String[] v = {cal, prot, fib, sug, sat, trans, chol, na};
        Nutrient[] n = {Nutrient.CALORIES, Nutrient.PROTEIN, Nutrient.DIETARY_FIBER, Nutrient.SUGAR,
                Nutrient.SATURATED_FAT, Nutrient.TRANS_FAT, Nutrient.CHOLESTEROL, Nutrient.SODIUM};
        for (int i = 0; i < v.length; i++) if (v[i] != null) m.put(n[i], d(v[i]));
        return m;
    }

    private static List<String> texts(List<Point> points) {
        return points.stream().map(Point::text).toList();
    }

    private static List<String> tagTexts(List<Tag> tags) {
        return tags.stream().map(Tag::text).toList();
    }

    @Test
    void 평가_말은_1일_기준치_5퍼센트와_20퍼센트로_가른다() {
        assertEquals(Level.LOW, NutrientEvaluator.level(Fact.SODIUM, d("100"), TRANS));      // 5%
        assertEquals(Level.MEDIUM, NutrientEvaluator.level(Fact.SODIUM, d("120"), TRANS));   // 6%
        assertEquals(Level.HIGH, NutrientEvaluator.level(Fact.SODIUM, d("400"), TRANS));     // 20%
        assertNull(NutrientEvaluator.level(Fact.SODIUM, null, TRANS));
        assertNull(NutrientEvaluator.dvPercent(Fact.TRANS_FAT, d("0.1")));
        assertEquals(Level.LOW, NutrientEvaluator.level(Fact.TRANS_FAT, d("0"), TRANS));
        assertEquals(Level.HIGH, NutrientEvaluator.level(Fact.TRANS_FAT, d("0.3"), TRANS));
    }

    @Test
    void 콜레스테롤만_100mg_이상이_많은_편() {
        assertEquals(Level.MEDIUM, NutrientEvaluator.level(Fact.CHOLESTEROL, d("60"), TRANS));   // 20% 지만 많은 편이 아니다
        assertEquals(Level.HIGH, NutrientEvaluator.level(Fact.CHOLESTEROL, d("100"), TRANS));
    }

    @Test
    void 닭가슴살_다이어트_단백질이_많고_아쉬운_점이_없다() {
        Map<Nutrient, BigDecimal> chicken = per100g("110", "23", null, "0", "0.5", "0", "60", "300");
        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, new Subject(chicken, null, false, false), TRANS);
        assertEquals(List.of("당류가 적은 편이에요", "포화지방이 적은 편이에요", "단백질이 많아요"), texts(wl.goods()));
        assertTrue(wl.bads().isEmpty());                         // 식이섬유 표시 없음은 아쉬운 점이 아니다, 콜레스테롤 60mg 도 아니다
        assertEquals(Tone.NEUTRAL, wl.tones().get(Nutrient.DIETARY_FIBER));
    }

    @Test
    void 달걀은_콜레스테롤이_많은_편() {
        Map<Nutrient, BigDecimal> egg = per100g("145", "12.5", null, "0.7", "3.1", "0", "370", "130");
        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, new Subject(egg, null, false, false), TRANS);
        assertEquals(List.of("콜레스테롤이 많은 편이에요", "포화지방이 많은 편이에요"), texts(wl.bads()));   // 1일 기준치 비율 큰 순
        assertEquals("당류는 적지만, 콜레스테롤이 많은 편이에요", wl.summary());
    }

    @Test
    void 근육_증가는_칼로리를_아쉬운_점으로_보이지_않고_가점과_선을_넘으면_좋은_점() {
        Map<Nutrient, BigDecimal> bar = per100g("420", "30", "7", "10", "2", "0", "10", "200");
        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, new Subject(bar, null, false, false), TRANS);
        assertEquals(List.of("칼로리가 높은 편이에요"), texts(wl.bads()));

        Evaluation mg = NutrientEvaluator.evaluate(GoalType.MUSCLE_GAIN, new Subject(bar, null, false, true), TRANS);
        assertTrue(mg.bads().isEmpty());
        assertEquals(List.of("단백질이 많아요", "칼로리가 높은 편이에요", "콜레스테롤이 적은 편이에요", "식이섬유가 많아요"), texts(mg.goods()));   // 식이섬유는 맨 뒤

        Evaluation mgNoBonus = NutrientEvaluator.evaluate(GoalType.MUSCLE_GAIN, new Subject(bar, null, false, false), TRANS);
        assertFalse(texts(mgNoBonus.goods()).contains("칼로리가 높은 편이에요"));
    }

    @Test
    void 음료는_칼로리_당류_포화지방_콜레스테롤_선이_절반() {
        Map<Nutrient, BigDecimal> drink = per100g("45", "0", null, "11", "0", "0", "0", "5");
        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, new Subject(drink, null, true, false), TRANS);
        assertEquals(List.of("당류가 많은 편이에요"), texts(wl.bads()));                     // 100mL당 10g 이상
        Evaluation solid = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, new Subject(drink, null, false, false), TRANS);
        assertTrue(solid.bads().isEmpty());                                                // 100g 이면 20g 선
    }

    @Test
    void 단백질_칩은_100mL당_5점5g() {
        Map<Nutrient, BigDecimal> shake = per100g("60", "8", null, "2", "0.4", "0", "5", "60");
        assertTrue(texts(NutrientEvaluator.evaluate(GoalType.MUSCLE_GAIN, new Subject(shake, null, true, false), TRANS).goods())
                .contains("단백질이 많아요"));
        assertFalse(texts(NutrientEvaluator.evaluate(GoalType.MUSCLE_GAIN, new Subject(shake, null, false, false), TRANS).goods())
                .contains("단백질이 많아요"));
    }

    @Test
    void 식이섬유_강조표시_세_갈래() {
        assertEquals(FiberClaim.NONE, NutrientEvaluator.fiberClaim(d("2"), d("300"), null));
        assertEquals(FiberClaim.SOURCE, NutrientEvaluator.fiberClaim(d("3"), d("300"), null));
        assertEquals(FiberClaim.SOURCE, NutrientEvaluator.fiberClaim(d("2"), d("300"), d("2.5")));   // 1회 제공량당
        assertEquals(FiberClaim.HIGH, NutrientEvaluator.fiberClaim(d("2"), d("60"), null));          // 100kcal당 3.3g
        assertEquals(FiberClaim.NONE, NutrientEvaluator.fiberClaim(null, d("60"), d("9")));
    }

    @Test
    void 카드_태그_다이어트_순서와_아쉬운_점_1개() {
        Map<Nutrient, BigDecimal> v = per100g("30", "12", "4", "0.3", "0.2", "0", "0", "500");
        List<Tag> tags = NutrientEvaluator.tags(GoalType.WEIGHT_LOSS, v, d("1"), false);
        assertEquals(List.of("저칼로리", "제로 슈거", "고나트륨"), tagTexts(tags));              // 아쉬운 점이 있으면 좋은 점 2개까지
        assertEquals(List.of("GOOD", "GOOD", "BAD"), tags.stream().map(Tag::type).toList());
    }

    @Test
    void 카드_태그_근육_증가는_저칼로리와_칼로리_아쉬운_점이_없다() {
        Map<Nutrient, BigDecimal> v = per100g("450", "25", "7", "4", "1", "0", "0", "100");
        assertEquals(List.of("고단백", "저당", "고식이섬유"), tagTexts(NutrientEvaluator.tags(GoalType.MUSCLE_GAIN, v, d("10"), false)));
        assertEquals(List.of("저당", "고단백", "고칼로리"), tagTexts(NutrientEvaluator.tags(GoalType.WEIGHT_LOSS, v, d("10"), false)));
    }

    @Test
    void 카드_태그_음료는_제로_칼로리와_절반_선() {
        Map<Nutrient, BigDecimal> v = per100g("2", "0", null, "0", "0", "0", "0", "10");
        assertEquals(List.of("제로 칼로리", "제로 슈거", "저지방"), tagTexts(NutrientEvaluator.tags(GoalType.WEIGHT_LOSS, v, d("0"), true)));
        Map<Nutrient, BigDecimal> cola = per100g("45", "0", null, "11", "0", "0", "0", "5");
        assertEquals(List.of("저지방", "고당"), tagTexts(NutrientEvaluator.tags(GoalType.WEIGHT_LOSS, cola, d("0"), true)));
    }
}
