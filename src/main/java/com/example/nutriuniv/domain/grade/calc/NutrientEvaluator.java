package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Nutrient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 성분 평가 말 (v3 CORE_004 · RSLT_004 · RSLT_005) — 순수 계산, 스프링 의존 없음.
 * <ul>
 *   <li>1일 기준치 % (식약처 고정값, 칼로리 구간과 무관): 5% 이하 적은 편, 6~19% 보통, 20% 이상 많은 편</li>
 *   <li>트랜스지방은 기준치가 없다 — 0 이면 적은 편, 등급 규칙의 감점 시작값을 넘으면 많은 편</li>
 *   <li>좋은 점 · 아쉬운 점은 100g(mL) 기준 단계로 고른다. 기준선 수치는 문장에 넣지 않는다</li>
 *   <li>목표가 가르는 것은 칼로리 방향 하나 — 감량은 적을수록, 근육 증가는 많을수록 좋다</li>
 * </ul>
 */
public final class NutrientEvaluator {

    public static final String NO_BADS = "이 목표에서 아쉬운 점이 없어요";
    public static final String MISSING_NOTE = "표시 없음";

    private NutrientEvaluator() {}

    public enum Level {
        LOW("적은 편"), MEDIUM("보통"), HIGH("많은 편");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Tone { GOOD, BAD, NEUTRAL }

    /** 영양성분표 한 줄 — 화면 순서. graded 는 등급 산식에 들어가는 8종(Nutrient)과의 대응. */
    public enum Fact {
        CALORIES(Nutrient.CALORIES, "칼로리", "kcal", 2000),
        CARBOHYDRATE(null, "탄수화물", "g", 324),
        SUGAR(Nutrient.SUGAR, "당류", "g", 100),
        DIETARY_FIBER(Nutrient.DIETARY_FIBER, "식이섬유", "g", 25),
        PROTEIN(Nutrient.PROTEIN, "단백질", "g", 55),
        FAT(null, "지방", "g", 54),
        SATURATED_FAT(Nutrient.SATURATED_FAT, "포화지방", "g", 15),
        TRANS_FAT(Nutrient.TRANS_FAT, "트랜스지방", "g", null),
        CHOLESTEROL(Nutrient.CHOLESTEROL, "콜레스테롤", "mg", 300),
        SODIUM(Nutrient.SODIUM, "나트륨", "mg", 2000);

        private final Nutrient graded;
        private final String label;
        private final String unit;
        private final Integer dailyValue;

        Fact(Nutrient graded, String label, String unit, Integer dailyValue) {
            this.graded = graded;
            this.label = label;
            this.unit = unit;
            this.dailyValue = dailyValue;
        }

        public Nutrient graded() { return graded; }
        public String label() { return label; }
        public String unit() { return unit; }
        public Integer dailyValue() { return dailyValue; }

        public static Fact of(Nutrient n) {
            for (Fact f : values()) {
                if (f.graded == n) return f;
            }
            throw new IllegalArgumentException("성분표에 없는 성분: " + n);
        }
    }

    /** 좋은 점 · 아쉬운 점 한 줄. missing 이면 값이 없어서 아쉬운 점에 들어간 것(note = 표시 없음). */
    public record Point(Nutrient nutrient, String text, boolean missing) {}

    public record Evaluation(List<Point> goods, List<Point> bads, String summary, Map<Nutrient, Tone> tones) {}

    // ── 1일 기준치 · 단계 ─────────────────────────────────────────────────────────

    /** 1일 기준치 % (정수 반올림). 값이 없거나 기준치가 없는 성분(트랜스지방)은 null. */
    public static Integer dvPercent(Fact f, BigDecimal value) {
        if (value == null || f.dailyValue == null) return null;
        return value.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(f.dailyValue), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** 단계. 값이 없으면 null. transFatThreshold 는 등급 규칙의 트랜스지방 감점 시작값(100g당). */
    public static Level level(Fact f, BigDecimal value, double transFatThreshold) {
        if (value == null) return null;
        if (f == Fact.TRANS_FAT) {
            if (value.signum() == 0) return Level.LOW;
            return value.doubleValue() > transFatThreshold ? Level.HIGH : Level.MEDIUM;
        }
        int pct = dvPercent(f, value);
        if (pct <= 5) return Level.LOW;
        if (pct >= 20) return Level.HIGH;
        return Level.MEDIUM;
    }

    /** 목표 기준 좋음 · 아쉬움. 보통이면 NEUTRAL. */
    public static Tone tone(GoalType goal, Nutrient n, Level level) {
        if (level == null) return n == Nutrient.DIETARY_FIBER ? Tone.BAD : Tone.NEUTRAL;
        if (level == Level.MEDIUM) return Tone.NEUTRAL;
        boolean moreIsBetter = switch (n) {
            case PROTEIN, DIETARY_FIBER -> true;
            case CALORIES -> goal == GoalType.MUSCLE_GAIN;
            default -> false;
        };
        return (level == Level.HIGH) == moreIsBetter ? Tone.GOOD : Tone.BAD;
    }

    // ── 목표별 평가 ───────────────────────────────────────────────────────────────

    /**
     * @param basis         「100g」 또는 「100mL」 — 칼로리 문장의 「100g당」
     * @param per100g       100g(mL) 기준 값. 등급이 있는 제품이면 식이섬유 외 7종은 non-null
     * @param contributions 산식 기여도(GradeFormula.Raw) — 좋은 점은 큰 순, 아쉬운 점은 작은 순으로 늘어놓는 데만 쓴다
     */
    public static Evaluation evaluate(GoalType goal, String basis, Map<Nutrient, BigDecimal> per100g,
                                      double transFatThreshold, Map<Nutrient, Double> contributions) {
        List<Point> goods = new ArrayList<>();
        List<Point> bads = new ArrayList<>();
        List<Point> missing = new ArrayList<>();
        Map<Nutrient, Tone> tones = new EnumMap<>(Nutrient.class);
        Map<Nutrient, Level> levels = new EnumMap<>(Nutrient.class);

        for (Nutrient n : Nutrient.values()) {
            BigDecimal v = per100g.get(n);
            Level level = level(Fact.of(n), v, transFatThreshold);
            Tone tone = tone(goal, n, level);
            tones.put(n, tone);
            if (level != null) levels.put(n, level);
            if (v == null) {
                if (tone == Tone.BAD) missing.add(new Point(n, subject(goal, basis, n, level) + " 표시가 없어요", true));
                continue;
            }
            if (tone == Tone.GOOD) goods.add(new Point(n, chip(goal, basis, n, level), false));
            if (tone == Tone.BAD) bads.add(new Point(n, chip(goal, basis, n, level), false));
        }

        goods.sort(Comparator.comparingDouble((Point p) -> -contributions.getOrDefault(p.nutrient(), 0.0)));
        bads.sort(Comparator.comparingDouble((Point p) -> contributions.getOrDefault(p.nutrient(), 0.0)));
        String summary = summary(goal, basis, goods, bads, levels);
        bads.addAll(missing);
        return new Evaluation(List.copyOf(goods), List.copyOf(bads), summary, tones);
    }

    // ── 카드 태그 (SRCH_001 · SRCH_007) ─────────────────────────────────────────────

    /** 목록 카드 태그 2개 — 좋은 점 1개 + 아쉬운 점 1개(한쪽이 없으면 다른 쪽으로 채움). 값 없음은 태그로 쓰지 않는다. */
    public static List<String> tags(GoalType goal, Evaluation ev) {
        List<Point> goods = ev.goods();
        List<Point> bads = ev.bads().stream().filter(p -> !p.missing()).toList();
        List<String> out = new ArrayList<>(2);
        if (!goods.isEmpty()) out.add(shortText(goal, goods.get(0).nutrient(), Tone.GOOD));
        if (!bads.isEmpty()) out.add(shortText(goal, bads.get(0).nutrient(), Tone.BAD));
        for (int i = 1; out.size() < 2 && i < goods.size(); i++) out.add(shortText(goal, goods.get(i).nutrient(), Tone.GOOD));
        for (int i = 1; out.size() < 2 && i < bads.size(); i++) out.add(shortText(goal, bads.get(i).nutrient(), Tone.BAD));
        return List.copyOf(out);
    }

    /** 짧은 말 — 「단백질 많음」「당류 적음」「칼로리 낮음」, 근육 증가의 많은 칼로리는 「칼로리 충분」. */
    public static String shortText(GoalType goal, Nutrient n, Tone tone) {
        boolean good = tone == Tone.GOOD;
        if (n == Nutrient.CALORIES) {
            if (goal == GoalType.MUSCLE_GAIN) return good ? "칼로리 충분" : "칼로리 낮음";
            return good ? "칼로리 낮음" : "칼로리 높음";
        }
        boolean moreIsBetter = n == Nutrient.PROTEIN || n == Nutrient.DIETARY_FIBER;
        return Fact.of(n).label() + (good == moreIsBetter ? " 많음" : " 적음");
    }

    // ── 문장 ─────────────────────────────────────────────────────────────────────

    /** 한 줄 해석 — 좋은 점과 아쉬운 점이 다 있으면 「단백질은 많지만, 100g당 칼로리가 높은 편이에요」. */
    static String summary(GoalType goal, String basis, List<Point> goods, List<Point> bads, Map<Nutrient, Level> levels) {
        if (!goods.isEmpty() && !bads.isEmpty()) {
            Nutrient g = goods.get(0).nutrient();
            return topic(goal, basis, g, levels.get(g)) + " " + stem(goal, g, levels.get(g)) + "지만, " + bads.get(0).text();
        }
        List<Point> one = !bads.isEmpty() ? bads : goods;
        if (one.isEmpty()) return "눈에 띄게 많거나 적은 성분이 없어요";
        if (one.size() == 1) return one.get(0).text();
        Nutrient a = one.get(0).nutrient();
        return subjectWithParticle(goal, basis, a, levels.get(a)) + " " + stem(goal, a, levels.get(a)) + "고, " + one.get(1).text();
    }

    /** 칩 문장 — 「당류가 많은 편이에요」, 「100g당 칼로리가 높은 편이에요」. 수치는 넣지 않는다. */
    static String chip(GoalType goal, String basis, Nutrient n, Level level) {
        return subjectWithParticle(goal, basis, n, level) + " " + adnominal(goal, n, level) + " 편이에요";
    }

    private static String subject(GoalType goal, String basis, Nutrient n, Level level) {
        if (n != Nutrient.CALORIES) return Fact.of(n).label();
        // 근육 증가에서 많은 쪽은 「칼로리가 충분한 편」 — 100g당을 붙이지 않는다
        return goal == GoalType.MUSCLE_GAIN && level == Level.HIGH ? "칼로리" : basis + "당 칼로리";
    }

    private static String subjectWithParticle(GoalType goal, String basis, Nutrient n, Level level) {
        String s = subject(goal, basis, n, level);
        return s + (hasFinalConsonant(s) ? "이" : "가");
    }

    private static String topic(GoalType goal, String basis, Nutrient n, Level level) {
        String s = subject(goal, basis, n, level);
        return s + (hasFinalConsonant(s) ? "은" : "는");
    }

    /** 어간 — 많/적, 칼로리는 높/낮, 근육 증가의 많은 칼로리는 충분하. */
    private static String stem(GoalType goal, Nutrient n, Level level) {
        boolean high = level == Level.HIGH;
        if (n == Nutrient.CALORIES) {
            if (high && goal == GoalType.MUSCLE_GAIN) return "충분하";
            return high ? "높" : "낮";
        }
        return high ? "많" : "적";
    }

    private static String adnominal(GoalType goal, Nutrient n, Level level) {
        String stem = stem(goal, n, level);
        return stem.endsWith("하") ? stem.substring(0, stem.length() - 1) + "한" : stem + "은";
    }

    private static boolean hasFinalConsonant(String s) {
        char c = s.charAt(s.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return false;
        return (c - 0xAC00) % 28 != 0;
    }
}
