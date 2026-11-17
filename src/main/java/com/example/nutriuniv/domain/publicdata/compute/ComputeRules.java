package com.example.nutriuniv.domain.publicdata.compute;

import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 34컬럼 계산의 칸 단위 규칙 (sync.md 「컬럼별 채우는 곳」). 순수 함수 — ComputeRulesTest 로 고정.
 */
public final class ComputeRules {

    private ComputeRules() {}

    // ── 공통 ────────────────────────────────────────────────────────────────

    /** 앞뒤 공백을 떼고, 비면 null. */
    public static String text(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** 숫자 문자열 → double. 비었거나 숫자가 아니면 null. 천 단위 쉼표는 뗀다. */
    public static Double number(String s) {
        String t = text(s);
        if (t == null) return null;
        try {
            double d = Double.parseDouble(t.replace(",", ""));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── report_no ────────────────────────────────────────────────────────────

    private static final Pattern REPORT_NO = Pattern.compile("\\d{11,16}");

    /** 「/」로 나눈 첫 번째에서 공백·하이픈을 빼고 숫자 11~16자리만. 아니면 null(수입식품 내부 코드 등). */
    public static String reportNo(String raw) {
        String t = text(raw);
        if (t == null) return null;
        int slash = t.indexOf('/');
        String first = (slash >= 0 ? t.substring(0, slash) : t).replaceAll("[\\s-]", "");
        return REPORT_NO.matcher(first).matches() ? first : null;
    }

    // ── barcode ──────────────────────────────────────────────────────────────

    /**
     * 원천 바코드 → 14자리 GTIN. 체크숫자가 맞아야 하고 아래는 뺀다.
     * <ul>
     *   <li>묶음 인디케이터 — 14자리 맨 앞이 0 이 아님(박스 단위 코드)</li>
     *   <li>매장 코드 — EAN-13 이 2 로 시작(20~29), UPC-12 가 2 · 4 로 시작(02 · 04)</li>
     * </ul>
     */
    public static String gtin14(String raw) {
        String t = text(raw);
        if (t == null) return null;
        Optional<String> g = BarcodeNormalizer.tryNormalize(t);
        if (g.isEmpty()) return null;
        String d = g.get();
        if (d.charAt(0) != '0') return null;
        String body = d.substring(1);                       // EAN-13 자리
        if (body.startsWith("2") || body.startsWith("02") || body.startsWith("04")) return null;
        return d;
    }

    // ── serving_size ─────────────────────────────────────────────────────────

    /** 총내용량. unit 은 g 또는 mL. */
    public record Amount(double value, String unit) {
        /** 34컬럼 표기 — 300g, 2450g, 90mL, 9.5g. */
        public String format() {
            return formatNumber(value) + unit;
        }
    }

    private static final Pattern AMOUNT = Pattern.compile("(?i)^(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l)$");

    /**
     * 「80.00g」 「2.2kg」 「900mL」 형태만 받는다. kg → g, L → mL 로 바꾼다. 묶음 표기(×, *, x 가 들어간 것)나 다른 모양은 null.
     */
    public static Amount amount(String raw) {
        String t = text(raw);
        if (t == null) return null;
        t = Normalizer.normalize(t, Normalizer.Form.NFKC).replace(" ", "");
        Matcher m = AMOUNT.matcher(t);
        if (!m.matches()) return null;
        double v = Double.parseDouble(m.group(1));
        if (v <= 0) return null;
        return switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "kg" -> new Amount(v * 1000, "g");
            case "g" -> new Amount(v, "g");
            case "l" -> new Amount(v * 1000, "mL");
            default -> new Amount(v, "mL");
        };
    }

    /** 숫자와 단위가 따로 온 경우(푸드QR 영양 표시 기준량 TCT + TCD). */
    public static Amount amount(String number, String unit) {
        String n = text(number);
        String u = text(unit);
        if (n == null || u == null) return null;
        return amount(n + u);
    }

    // ── 영양성분 ─────────────────────────────────────────────────────────────

    /** 영양성분 10종 — 34컬럼 순서. */
    public static final int NUTRIENTS = 10;

    /** 영양성분DB 필드 — 열량 · 나트륨 · 탄수화물 · 당류 · 지방 · 트랜스지방 · 포화지방 · 콜레스테롤 · 식이섬유 · 단백질 (100g 당). */
    public static final String[] NUTRI_DB_FIELDS = {
            "AMT_NUM1", "AMT_NUM13", "AMT_NUM6", "AMT_NUM7", "AMT_NUM4",
            "AMT_NUM25", "AMT_NUM24", "AMT_NUM23", "AMT_NUM8", "AMT_NUM3"};

    /** 푸드QR 영양 항목 이름(NIRWMT_NM, 괄호 · 공백을 뗀 값) → 10종 순서 번호. */
    private static final Map<String, Integer> FOODQR_NUTRIENT = Map.ofEntries(
            Map.entry("열량", 0), Map.entry("에너지", 0),
            Map.entry("나트륨", 1),
            Map.entry("탄수화물", 2),
            Map.entry("당류", 3), Map.entry("총당류", 3),
            Map.entry("지방", 4), Map.entry("총지방", 4),
            Map.entry("트랜스지방", 5),
            Map.entry("포화지방", 6),
            Map.entry("콜레스테롤", 7),
            Map.entry("식이섬유", 8),
            Map.entry("단백질", 9));

    private static final Pattern PAREN = Pattern.compile("[(\\[（].*?[)\\]）]");

    /** 푸드QR 영양 항목 이름 → 순서 번호(0~9). 10종이 아니면 -1. */
    public static int foodQrNutrientIndex(String name) {
        String t = text(name);
        if (t == null) return -1;
        t = PAREN.matcher(Normalizer.normalize(t, Normalizer.Form.NFKC)).replaceAll("").replaceAll("\\s", "");
        return FOODQR_NUTRIENT.getOrDefault(t, -1);
    }

    /** 푸드QR 영양 표시 기준(NTRTN_INDCT_CD). */
    public enum Basis { PER_100, TOTAL, UNIT }

    /**
     * 표시 기준 → 종류. 「100g당 · 100ml당」 = PER_100, 「총 내용량당」 = TOTAL, 「단위 내용량당 · 1회 제공량당」 = UNIT.
     * 코드 값이 이 글자를 담지 않으면 기준량(TCT)이 100 이면 PER_100, 아니면 TOTAL 로 본다.
     */
    public static Basis basis(String code, Double basisAmount) {
        String t = text(code);
        if (t != null) {
            String c = t.replace(" ", "");
            if (c.contains("단위") || c.contains("1회") || c.contains("회제공")) return Basis.UNIT;
            if (c.contains("총")) return Basis.TOTAL;
            if (c.contains("100")) return Basis.PER_100;
        }
        return basisAmount != null && basisAmount == 100.0 ? Basis.PER_100 : Basis.TOTAL;
    }

    /** 푸드QR 표시값 → 100g 당. amount 는 TOTAL 이면 총내용량, UNIT 이면 단위 내용량. 모르면 null. */
    public static Double per100(double value, Basis basis, Double amount) {
        return switch (basis) {
            case PER_100 -> value;
            case TOTAL, UNIT -> amount == null || amount <= 0 ? null : value * 100 / amount;
        };
    }

    /** 한 봉지 전체 = 100g 당 × 총내용량 / 100. 둘 중 하나라도 없으면 null. */
    public static Double whole(Double per100, Amount serving) {
        if (per100 == null || serving == null) return null;
        return per100 * serving.value() / 100;
    }

    // ── 지명 (brand 조회표 7) ─────────────────────────────────────────────────

    /** 주소 둘째 낱말(예: 포천시)에서 끝의 시 · 군 · 구를 뗀 값. 2글자 미만이면 null. */
    public static String placeName(String secondWord) {
        String t = text(secondWord);
        if (t == null) return null;
        t = Normalizer.normalize(t, Normalizer.Form.NFKC);
        if (t.length() > 1 && (t.endsWith("시") || t.endsWith("군") || t.endsWith("구"))) t = t.substring(0, t.length() - 1);
        return t.length() >= 2 && t.chars().allMatch(c -> c >= '가' && c <= '힣') ? t : null;
    }

    // ── 표기 ─────────────────────────────────────────────────────────────────

    /** content_hash 와 같은 숫자 표기(소수 둘째 자리 HALF_EVEN, 끝 0 제거). null 은 null. */
    public static String formatNumber(Double d) {
        return d == null ? null : com.example.nutriuniv.domain.product.util.ContentHashCalculator.formatNumber(d);
    }

    /** 비교용 정규화 — NFKC, 소문자, 공백 하나로. */
    public static String norm(String s) {
        String t = text(s);
        if (t == null) return "";
        return Normalizer.normalize(t, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** 날짜 비교용 — 숫자만 남긴다(2026-11-15 · 20261115 를 같게). */
    public static String digits(String s) {
        String t = text(s);
        return t == null ? "" : t.replaceAll("\\D", "");
    }
}