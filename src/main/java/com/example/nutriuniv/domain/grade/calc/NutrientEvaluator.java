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
 * 성분 평가 말 · 좋은 점 · 아쉬운 점 · 목록 카드 태그 (v3 CORE_004 · RSLT_005 · SRCH_001) — 순수 계산, 스프링 의존 없음.
 * <ul>
 *   <li>성분 줄 평가 말: 1일 기준치 5% 이하 적은 편, 6~19% 보통, 20% 이상 많은 편. 콜레스테롤만 100mg 이상이 많은 편</li>
 *   <li>아쉬운 점: 칼로리 · 당류 · 포화지방 · 나트륨 · 콜레스테롤이 「많은 편」 선 이상일 때(근육 증가는 칼로리 없음).
 *       성분 기준 단위가 100mL 이면 칼로리 · 당류 · 포화지방 · 콜레스테롤 선이 절반, 나트륨은 그대로</li>
 *   <li>좋은 점: 「적은 편」(5% 이하)인 제한 성분, 「단백질이 많아요」(100g당 11g · 100mL당 5.5g 이상),
 *       식이섬유 「함유」 · 「고」(두 목표 · 근육 증가는 맨 뒤), 근육 증가 칼로리(가점이 붙고 「많은 편」 선 이상)</li>
 *   <li>식이섬유 값이 없어도 아쉬운 점에 넣지 않는다(11-18)</li>
 *   <li>카드 태그: 좋은 점은 식약처 영양강조표시 기준, 아쉬운 점은 1일 기준치 비율이 가장 큰 1개, 최대 3개</li>
 * </ul>
 */
public final class NutrientEvaluator {

    public static final String NO_BADS = "이 목표에서 아쉬운 점이 없어요";
    public static final String MISSING_NOTE = "표시 없음";

    /** 콜레스테롤 「많은 편」 — 1일 기준치 300mg 의 3분의 1(한 끼 몫 · 11-23). */
    static final BigDecimal CHOLESTEROL_HIGH = new BigDecimal("100");

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

    /** 영양성분표 한 줄 — 화면 순서. graded 는 점수 식에 들어가는 8종(Nutrient)과의 대응. */
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

    /** 좋은 점 · 아쉬운 점 한 줄. missing 은 남겨 둔 칸(값 없음은 이제 아쉬운 점에 넣지 않아 늘 false). */
    public record Point(Nutrient nutrient, String text, boolean missing) {}

    public record Evaluation(List<Point> goods, List<Point> bads, String summary, Map<Nutrient, Tone> tones) {}

    /** 평가에 필요한 제품 값 — 100g(mL)당 8종, 1회 제공량당 식이섬유(없으면 null), 100mL 표시 여부, 근육 증가 칼로리 가점 여부. */
    public record Subject(Map<Nutrient, BigDecimal> per100g, BigDecimal fiberPerServing, boolean perMl, boolean calorieBonus) {}

    /** 카드 태그 한 개 — GOOD(좋은 점) | BAD(아쉬운 점). */
    public record Tag(String text, String type) {}

    // ── 1일 기준치 · 단계 ─────────────────────────────────────────────────────────

    /** 1일 기준치 % (정수 반올림). 값이 없거나 기준치가 없는 성분(트랜스지방)은 null. */
    public static Integer dvPercent(Fact f, BigDecimal value) {
        if (value == null || f.dailyValue == null) return null;
        return value.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(f.dailyValue), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** 성분 줄 평가 말. 값이 없으면 null. transFatThreshold 는 트랜스지방 감점 시작값(100g당). */
    public static Level level(Fact f, BigDecimal value, double transFatThreshold) {
        if (value == null) return null;
        if (f == Fact.TRANS_FAT) {
            if (value.signum() == 0) return Level.LOW;
            return value.doubleValue() > transFatThreshold ? Level.HIGH : Level.MEDIUM;
        }
        int pct = dvPercent(f, value);
        if (pct <= 5) return Level.LOW;
        if (f == Fact.CHOLESTEROL) return value.compareTo(CHOLESTEROL_HIGH) >= 0 ? Level.HIGH : Level.MEDIUM;
        if (pct >= 20) return Level.HIGH;
        return Level.MEDIUM;
    }

    /** 아쉬운 점 선(100g당 · 1일 기준치 20% · 콜레스테롤 100mg). 100mL 표시는 칼로리 · 당류 · 포화지방 · 콜레스테롤만 절반. */
    static BigDecimal badLine(Nutrient n, boolean perMl) {
        BigDecimal line = switch (n) {
            case CALORIES -> new BigDecimal("400");
            case SUGAR -> new BigDecimal("20");
            case SATURATED_FAT -> new BigDecimal("3");
            case SODIUM -> new BigDecimal("400");
            case CHOLESTEROL -> CHOLESTEROL_HIGH;
            default -> null;
        };
        if (line == null || !perMl || n == Nutrient.SODIUM) return line;
        return line.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /** 아쉬운 점에 오를 수 있는 성분 — 근육 증가는 칼로리를 아쉬운 점으로 보이지 않는다. */
    static boolean badCandidate(GoalType goal, Nutrient n) {
        return switch (n) {
            case CALORIES -> goal != GoalType.MUSCLE_GAIN;
            case SUGAR, SATURATED_FAT, SODIUM, CHOLESTEROL -> true;
            default -> false;
        };
    }

    /** 1일 기준치 대비 비율 — 아쉬운 점 순서(큰 것 먼저). */
    private static double dvRatio(Nutrient n, BigDecimal v) {
        Integer dv = Fact.of(n).dailyValue();
        return dv == null || v == null ? 0.0 : v.doubleValue() / dv;
    }

    // ── 식이섬유 강조표시 (국내 기준 세 갈래) ───────────────────────────────────────

    public enum FiberClaim { NONE, SOURCE, HIGH }

    /**
     * 「함유」: 100g(mL)당 3g · 100kcal당 1.5g · 1회 제공량당 2.5g 중 하나 이상, 「고」: 그 2배.
     * 1회 제공량 갈래는 칩에만 쓰고 점수에는 넣지 않는다(11-24). 칼로리가 0 이면 100kcal당은 보지 않는다.
     */
    public static FiberClaim fiberClaim(BigDecimal fiber100, BigDecimal kcal100, BigDecimal fiberServing) {
        if (fiber100 == null) return FiberClaim.NONE;
        double f = fiber100.doubleValue();
        double per100kcal = kcal100 != null && kcal100.signum() > 0 ? f / kcal100.doubleValue() * 100.0 : -1.0;
        double s = fiberServing == null ? -1.0 : fiberServing.doubleValue();
        if (f >= 6.0 || per100kcal >= 3.0 || s >= 5.0) return FiberClaim.HIGH;
        if (f >= 3.0 || per100kcal >= 1.5 || s >= 2.5) return FiberClaim.SOURCE;
        return FiberClaim.NONE;
    }

    // ── 목표별 평가 (CORE_004) ────────────────────────────────────────────────────

    public static Evaluation evaluate(GoalType goal, Subject p, double transFatThreshold) {
        Map<Nutrient, BigDecimal> v = p.per100g();
        boolean muscle = goal == GoalType.MUSCLE_GAIN;

        // 아쉬운 점 — 1일 기준치 비율이 큰 순
        List<Nutrient> badOnes = new ArrayList<>();
        for (Nutrient n : Nutrient.values()) {
            BigDecimal val = v.get(n);
            if (val == null || !badCandidate(goal, n)) continue;
            if (val.compareTo(badLine(n, p.perMl())) >= 0) badOnes.add(n);
        }
        badOnes.sort(Comparator.comparingDouble((Nutrient n) -> -dvRatio(n, v.get(n))));

        // 좋은 점 — 목표별 순서
        List<Nutrient> goodOnes = new ArrayList<>();
        Map<Nutrient, String> goodText = new EnumMap<>(Nutrient.class);
        BigDecimal protein = v.get(Nutrient.PROTEIN);
        boolean highProtein = protein != null && protein.compareTo(new BigDecimal(p.perMl() ? "5.5" : "11")) >= 0;
        BigDecimal kcal = v.get(Nutrient.CALORIES);
        boolean muscleCalorie = muscle && p.calorieBonus() && kcal != null
                && kcal.compareTo(badLine(Nutrient.CALORIES, p.perMl())) >= 0;
        FiberClaim fiber = fiberClaim(v.get(Nutrient.DIETARY_FIBER), kcal, p.fiberPerServing());

        List<Nutrient> order = muscle
                ? List.of(Nutrient.PROTEIN, Nutrient.CALORIES, Nutrient.SUGAR, Nutrient.SATURATED_FAT, Nutrient.SODIUM, Nutrient.CHOLESTEROL, Nutrient.DIETARY_FIBER)
                : List.of(Nutrient.CALORIES, Nutrient.SUGAR, Nutrient.SATURATED_FAT, Nutrient.SODIUM, Nutrient.CHOLESTEROL, Nutrient.PROTEIN, Nutrient.DIETARY_FIBER);
        for (Nutrient n : order) {
            String text = switch (n) {
                case PROTEIN -> highProtein ? "단백질이 많아요" : null;
                case DIETARY_FIBER -> switch (fiber) {
                    case HIGH -> "식이섬유가 많아요";
                    case SOURCE -> "식이섬유가 들어 있어요";
                    case NONE -> null;
                };
                case CALORIES -> muscle
                        ? (muscleCalorie ? "칼로리가 높은 편이에요" : null)
                        : (isLow(Nutrient.CALORIES, v) ? "칼로리가 낮은 편이에요" : null);
                case SUGAR, SATURATED_FAT, SODIUM, CHOLESTEROL ->
                        isLow(n, v) && !badOnes.contains(n) ? withParticle(Fact.of(n).label()) + " 적은 편이에요" : null;
                default -> null;
            };
            if (text != null) {
                goodOnes.add(n);
                goodText.put(n, text);
            }
        }

        List<Point> goods = goodOnes.stream().map(n -> new Point(n, goodText.get(n), false)).toList();
        List<Point> bads = badOnes.stream().map(n -> new Point(n, badText(n), false)).toList();

        Map<Nutrient, Tone> tones = new EnumMap<>(Nutrient.class);
        for (Nutrient n : Nutrient.values()) {
            tones.put(n, goodOnes.contains(n) ? Tone.GOOD : badOnes.contains(n) ? Tone.BAD : Tone.NEUTRAL);
        }
        return new Evaluation(goods, bads, summary(goods, bads), tones);
    }

    private static boolean isLow(Nutrient n, Map<Nutrient, BigDecimal> v) {
        BigDecimal val = v.get(n);
        if (val == null) return false;
        Integer pct = dvPercent(Fact.of(n), val);
        return pct != null && pct <= 5;
    }

    static String badText(Nutrient n) {
        return n == Nutrient.CALORIES ? "칼로리가 높은 편이에요" : withParticle(Fact.of(n).label()) + " 많은 편이에요";
    }

    // ── 카드 태그 (SRCH_001) ────────────────────────────────────────────────────────

    /**
     * 목록 카드 태그 — 좋은 점(식약처 영양강조표시 기준)을 앞에, 아쉬운 점 1개(1일 기준치 비율이 가장 큰 성분)를 맨 뒤에, 최대 3개.
     * 아쉬운 점이 있으면 좋은 점은 2개까지. 「제로」가 해당되면 「저」 대신 「제로」.
     *
     * @param fat100 지방(100g · mL당) — 저지방 판정. 없으면 저지방을 보지 않는다
     */
    public static List<Tag> tags(GoalType goal, Map<Nutrient, BigDecimal> v, BigDecimal fat100, boolean perMl) {
        boolean muscle = goal == GoalType.MUSCLE_GAIN;
        BigDecimal kcal = v.get(Nutrient.CALORIES);
        BigDecimal sugar = v.get(Nutrient.SUGAR);
        BigDecimal protein = v.get(Nutrient.PROTEIN);

        String calorie = null;
        if (!muscle && kcal != null) {
            if (perMl && lt(kcal, "4")) calorie = "제로 칼로리";
            else if (lt(kcal, perMl ? "20" : "40")) calorie = "저칼로리";
        }
        String sugarTag = sugar == null ? null : lt(sugar, "0.5") ? "제로 슈거" : lt(sugar, perMl ? "2.5" : "5") ? "저당" : null;
        String fatTag = fat100 != null && lt(fat100, perMl ? "1.5" : "3") ? "저지방" : null;
        boolean highProtein = protein != null && (protein.compareTo(new BigDecimal(perMl ? "5.5" : "11")) >= 0
                || (kcal != null && kcal.signum() > 0 && protein.doubleValue() * 100.0 / kcal.doubleValue() >= 5.5));
        String proteinTag = highProtein ? "고단백" : null;
        String fiberTag = switch (fiberClaim(v.get(Nutrient.DIETARY_FIBER), kcal, null)) {
            case HIGH -> "고식이섬유";
            case SOURCE -> "식이섬유 함유";
            case NONE -> null;
        };

        List<String> goods = new ArrayList<>();
        for (String t : muscle
                ? new String[]{proteinTag, sugarTag, fatTag, fiberTag}
                : new String[]{calorie, sugarTag, fatTag, proteinTag, fiberTag}) {
            if (t != null) goods.add(t);
        }

        Nutrient worst = null;
        for (Nutrient n : Nutrient.values()) {
            BigDecimal val = v.get(n);
            if (val == null || !badCandidate(goal, n) || val.compareTo(badLine(n, perMl)) < 0) continue;
            if (worst == null || dvRatio(n, val) > dvRatio(worst, v.get(worst))) worst = n;
        }

        List<Tag> out = new ArrayList<>(3);
        int goodMax = worst == null ? 3 : 2;
        for (int i = 0; i < goods.size() && i < goodMax; i++) out.add(new Tag(goods.get(i), Tone.GOOD.name()));
        if (worst != null) out.add(new Tag(badTag(worst), Tone.BAD.name()));
        return List.copyOf(out);
    }

    static String badTag(Nutrient n) {
        return switch (n) {
            case CALORIES -> "고칼로리";
            case SUGAR -> "고당";
            case SATURATED_FAT -> "고포화지방";
            case SODIUM -> "고나트륨";
            case CHOLESTEROL -> "고콜레스테롤";
            default -> throw new IllegalArgumentException("아쉬운 점 태그가 없는 성분: " + n);
        };
    }

    private static boolean lt(BigDecimal v, String line) {
        return v.compareTo(new BigDecimal(line)) < 0;
    }

    // ── 한 줄 해석 (3-9 점수 확인) ────────────────────────────────────────────────

    /** 좋은 점과 아쉬운 점이 다 있으면 「단백질은 많지만, 당류가 많은 편이에요」. */
    static String summary(List<Point> goods, List<Point> bads) {
        if (!goods.isEmpty() && !bads.isEmpty()) {
            return clause(goods.get(0), "지만, ") + bads.get(0).text();
        }
        List<Point> one = !bads.isEmpty() ? bads : goods;
        if (one.isEmpty()) return "눈에 띄게 많거나 적은 성분이 없어요";
        if (one.size() == 1) return one.get(0).text();
        return clause(one.get(0), "고, ") + one.get(1).text();
    }

    /** 「단백질이 많아요」 → 「단백질은 많」 + 이음말. */
    private static String clause(Point p, String tail) {
        String t = p.text();
        int sp = t.indexOf(' ');
        String subject = t.substring(0, sp - 1);       // 조사 하나를 뗀다
        String rest = t.substring(sp + 1);
        String stem;
        if (rest.startsWith("많")) stem = "많";
        else if (rest.startsWith("적")) stem = "적";
        else if (rest.startsWith("높")) stem = "높";
        else if (rest.startsWith("낮")) stem = "낮";
        else if (rest.startsWith("들어 있")) stem = "들어 있";
        else stem = rest;
        return topic(subject) + " " + stem + tail;
    }

    private static String withParticle(String s) {
        return s + (hasFinalConsonant(s) ? "이" : "가");
    }

    private static String topic(String s) {
        return s + (hasFinalConsonant(s) ? "은" : "는");
    }

    private static boolean hasFinalConsonant(String s) {
        char c = s.charAt(s.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return false;
        return (c - 0xAC00) % 28 != 0;
    }
}
