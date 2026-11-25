package com.example.nutriuniv.domain.link.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상품명에 적힌 1개 용량과 개수 (DATA_006 · 11-24). 순수 함수 — 코드 규칙으로만 읽는다(AI 판정 없음).
 * <ul>
 *   <li>용량: 「700g」「1.2kg」「500ml」「1.5L」 → g 또는 mL 로 맞춘다. mg 은 성분 표기라 용량으로 보지 않는다</li>
 *   <li>개수: 「10개」「12개입」「3봉」「6팩」「24병」「30캔」「5포」 또는 용량 뒤 「x 12」</li>
 *   <li>같은 이름에 용량이나 개수가 서로 다르게 여러 번 적혀 있으면 그 값은 읽지 않는다(여러 가지로 적힘 · 11-03)</li>
 * </ul>
 */
public record PackSize(Unit unit, BigDecimal amount, Integer count) {

    public enum Unit {
        G("g"), ML("mL");

        private final String label;

        Unit(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 두 상품명의 용량 비교 결과. */
    public enum Match {
        /** 양쪽 다 용량 · 개수가 적혀 있고 둘 다 같음 — 같은 용량으로 연결 */
        SAME,
        /** 양쪽 다 적혀 있는데 용량이나 개수가 다름 — 다른 용량으로 연결 */
        OTHER,
        /** 한쪽이라도 용량이나 개수가 없음 — 자동으로 연결하지 않고 운영자가 확인 */
        UNKNOWN
    }

    public static final PackSize NONE = new PackSize(null, null, null);

    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l)(?![a-wyz])", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile(
            "(\\d+)\\s*(?:개입|개|입|봉|팩|병|캔|포|ea|pcs)(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMES = Pattern.compile(
            "(?:kg|g|ml|l)\\s*[x×*]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public static PackSize parse(String name) {
        if (name == null || name.isBlank()) return NONE;
        // 천 단위 쉼표(「1,000g」)는 떼고, 남은 쉼표는 칸으로 본다(「700g, 1개」)
        String s = name.replaceAll("(\\d),(\\d{3})(?!\\d)", "$1$2").replace(',', ' ').replace('，', ' ');
        Set<String> units = new LinkedHashSet<>();
        Set<BigDecimal> amounts = new LinkedHashSet<>();
        Matcher a = AMOUNT.matcher(s);
        while (a.find()) {
            BigDecimal v = new BigDecimal(a.group(1));
            String u = a.group(2).toLowerCase();
            Unit unit = u.equals("kg") || u.equals("g") ? Unit.G : Unit.ML;
            if (u.equals("kg") || u.equals("l")) v = v.multiply(BigDecimal.valueOf(1000));
            if (v.signum() <= 0) continue;
            units.add(unit.name());
            amounts.add(v.stripTrailingZeros());
        }
        Set<Integer> counts = new LinkedHashSet<>();
        Matcher c = COUNT.matcher(s);
        while (c.find()) addCount(counts, c.group(1));
        Matcher t = TIMES.matcher(s);
        while (t.find()) addCount(counts, t.group(1));

        Unit unit = null;
        BigDecimal amount = null;
        if (units.size() == 1 && amounts.size() == 1) {
            unit = Unit.valueOf(units.iterator().next());
            amount = amounts.iterator().next();
        }
        Integer count = counts.size() == 1 ? counts.iterator().next() : null;
        return new PackSize(unit, amount, count);
    }

    private static void addCount(Set<Integer> counts, String raw) {
        try {
            int n = Integer.parseInt(raw);
            if (n > 0) counts.add(n);
        } catch (NumberFormatException ignored) {
            // 너무 긴 숫자는 개수가 아니다
        }
    }

    /** 1개 용량과 개수가 둘 다 읽혔는가. */
    public boolean complete() {
        return unit != null && amount != null && count != null;
    }

    /** 우리 제품명과 쿠팡 상품명의 용량 비교 — 둘 다 용량 · 개수가 적혀 있을 때만 SAME / OTHER. */
    public static Match compare(String ourName, String theirName) {
        PackSize ours = parse(ourName);
        PackSize theirs = parse(theirName);
        if (!ours.complete() || !theirs.complete()) return Match.UNKNOWN;
        boolean same = ours.unit == theirs.unit
                && ours.amount.compareTo(theirs.amount) == 0
                && ours.count.equals(theirs.count);
        return same ? Match.SAME : Match.OTHER;
    }

    /** 100g(mL)당 값 = 판매가 ÷ (1개 용량 × 개수) × 100. 용량 · 개수가 다 읽히지 않으면 null. */
    public Integer per100(Integer price) {
        if (price == null || !complete()) return null;
        BigDecimal total = amount.multiply(BigDecimal.valueOf(count));
        return BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP).intValueExact();
    }

    /** 1개당 값 = 판매가 ÷ 개수. 개수가 안 적혔으면 null(1개로 보지 않는다 · 11-24). */
    public Integer perUnit(Integer price) {
        if (price == null || count == null) return null;
        return BigDecimal.valueOf(price).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValueExact();
    }
}
