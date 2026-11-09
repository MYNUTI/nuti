package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Evaluation;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Fact;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Level;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Point;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Tone;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NutrientEvaluatorTest {

    private static final double TRANS = 0.2;

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /** 칼로리 · 단백질 · 식이섬유 · 당류 · 포화지방 · 트랜스지방 · 콜레스테롤 · 나트륨 (100g 기준). null 이면 넣지 않는다. */
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

    @Test
    void 일일기준치_퍼센트와_단계_경계() {
        assertEquals(5, NutrientEvaluator.dvPercent(Fact.SODIUM, d("100")));
        assertEquals(Level.LOW, NutrientEvaluator.level(Fact.SODIUM, d("100"), TRANS));       // 5%
        assertEquals(Level.MEDIUM, NutrientEvaluator.level(Fact.SODIUM, d("120"), TRANS));    // 6%
        assertEquals(Level.MEDIUM, NutrientEvaluator.level(Fact.SODIUM, d("380"), TRANS));    // 19%
        assertEquals(Level.HIGH, NutrientEvaluator.level(Fact.SODIUM, d("400"), TRANS));      // 20%
        assertEquals(20, NutrientEvaluator.dvPercent(Fact.PROTEIN, d("11")));
        assertNull(NutrientEvaluator.dvPercent(Fact.SODIUM, null));
        assertNull(NutrientEvaluator.level(Fact.SODIUM, null, TRANS));
    }

    @Test
    void 트랜스지방은_기준치가_없고_0이면_적은편_감점선_넘으면_많은편() {
        assertNull(NutrientEvaluator.dvPercent(Fact.TRANS_FAT, d("0.1")));
        assertEquals(Level.LOW, NutrientEvaluator.level(Fact.TRANS_FAT, d("0"), TRANS));
        assertEquals(Level.MEDIUM, NutrientEvaluator.level(Fact.TRANS_FAT, d("0.2"), TRANS));
        assertEquals(Level.HIGH, NutrientEvaluator.level(Fact.TRANS_FAT, d("0.3"), TRANS));
    }

    @Test
    void 칼로리_방향만_목표에_따라_다르다() {
        assertEquals(Tone.BAD, NutrientEvaluator.tone(GoalType.WEIGHT_LOSS, Nutrient.CALORIES, Level.HIGH));
        assertEquals(Tone.GOOD, NutrientEvaluator.tone(GoalType.MUSCLE_GAIN, Nutrient.CALORIES, Level.HIGH));
        assertEquals(Tone.GOOD, NutrientEvaluator.tone(GoalType.WEIGHT_LOSS, Nutrient.CALORIES, Level.LOW));
        assertEquals(Tone.BAD, NutrientEvaluator.tone(GoalType.MUSCLE_GAIN, Nutrient.CALORIES, Level.LOW));
        assertEquals(Tone.GOOD, NutrientEvaluator.tone(GoalType.WEIGHT_LOSS, Nutrient.PROTEIN, Level.HIGH));
        assertEquals(Tone.BAD, NutrientEvaluator.tone(GoalType.MUSCLE_GAIN, Nutrient.SUGAR, Level.HIGH));
        assertEquals(Tone.NEUTRAL, NutrientEvaluator.tone(GoalType.MUSCLE_GAIN, Nutrient.SUGAR, Level.MEDIUM));
    }

    @Test
    void 단백질바_감량은_칼로리가_아쉽고_근육증가는_칼로리가_좋다() {
        // 400kcal(20%) · 단백질 30g(55%) · 식이섬유 2g(8%) · 당류 3g(3%) · 포화지방 2g(13%) · 트랜스 0 · 콜레스테롤 10mg(3%) · 나트륨 200mg(10%)
        Map<Nutrient, BigDecimal> bar = per100g("400", "30", "2", "3", "2", "0", "10", "200");
        Map<Nutrient, Double> contrib = Map.of(Nutrient.CALORIES, -0.5, Nutrient.PROTEIN, 1.0);

        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, "100g", bar, TRANS, contrib);
        assertEquals("단백질은 많지만, 100g당 칼로리가 높은 편이에요", wl.summary());
        assertEquals(List.of("100g당 칼로리가 높은 편이에요"), texts(wl.bads()));
        assertEquals("단백질이 많은 편이에요", wl.goods().get(0).text());
        assertTrue(texts(wl.goods()).containsAll(List.of("당류가 적은 편이에요", "트랜스지방이 적은 편이에요", "콜레스테롤이 적은 편이에요")));

        Evaluation mg = NutrientEvaluator.evaluate(GoalType.MUSCLE_GAIN, "100g", bar, TRANS, Map.of(Nutrient.PROTEIN, 1.0, Nutrient.CALORIES, 0.6));
        assertTrue(mg.bads().isEmpty());
        assertEquals(List.of("단백질이 많은 편이에요", "칼로리가 충분한 편이에요"), texts(mg.goods()).subList(0, 2));
        assertEquals("단백질이 많고, 칼로리가 충분한 편이에요", mg.summary());
    }

    @Test
    void 식이섬유_표시가_없으면_아쉬운점_맨뒤에_missing() {
        Map<Nutrient, BigDecimal> soda = per100g("45", "0", null, "11", "0", "0", "0", "10");
        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, "100mL", soda, TRANS, Map.of(Nutrient.SUGAR, -0.06));
        Point last = wl.bads().get(wl.bads().size() - 1);
        assertEquals(Nutrient.DIETARY_FIBER, last.nutrient());
        assertTrue(last.missing());
        assertEquals("식이섬유 표시가 없어요", last.text());
        assertTrue(texts(wl.bads()).contains("단백질이 적은 편이에요"));
        assertTrue(texts(wl.goods()).contains("100mL당 칼로리가 낮은 편이에요"));
        assertFalse(wl.summary().contains("표시"));
    }

    @Test
    void 전부_보통이면_고정_문장() {
        // 200kcal(10%) · 단백질 5g(9%) · 식이섬유 3g(12%) · 당류 10g(10%) · 포화지방 1.5g(10%) · 트랜스 0.1 · 콜레스테롤 30mg(10%) · 나트륨 200mg(10%)
        Map<Nutrient, BigDecimal> mid = per100g("200", "5", "3", "10", "1.5", "0.1", "30", "200");
        Evaluation wl = NutrientEvaluator.evaluate(GoalType.WEIGHT_LOSS, "100g", mid, TRANS, Map.of());
        assertTrue(wl.goods().isEmpty());
        assertTrue(wl.bads().isEmpty());
        assertEquals("눈에 띄게 많거나 적은 성분이 없어요", wl.summary());
    }

    @Test
    void 카드_태그는_좋은점_하나_아쉬운점_하나_값없음은_빼고_모자라면_채운다() {
        Evaluation ev = new Evaluation(
                List.of(new Point(Nutrient.PROTEIN, "", false), new Point(Nutrient.SUGAR, "", false)),
                List.of(new Point(Nutrient.DIETARY_FIBER, "", true), new Point(Nutrient.CALORIES, "", false)),
                "", Map.of());
        assertEquals(List.of("단백질 많음", "칼로리 높음"), NutrientEvaluator.tags(GoalType.WEIGHT_LOSS, ev));
        assertEquals(List.of("단백질 많음", "칼로리 낮음"), NutrientEvaluator.tags(GoalType.MUSCLE_GAIN, ev));

        Evaluation goodsOnly = new Evaluation(
                List.of(new Point(Nutrient.SODIUM, "", false), new Point(Nutrient.CALORIES, "", false)), List.of(), "", Map.of());
        assertEquals(List.of("나트륨 적음", "칼로리 충분"), NutrientEvaluator.tags(GoalType.MUSCLE_GAIN, goodsOnly));
        assertEquals(List.of(), NutrientEvaluator.tags(GoalType.WEIGHT_LOSS, new Evaluation(List.of(), List.of(), "", Map.of())));
    }
}
