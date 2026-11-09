package com.example.nutriuniv.domain.product.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1회 제공량 문자열(「300g」「1봉지(30g)」「200 mL」) → 숫자 + 단위 (v3 RSLT_005 「1회 제공량을 숫자 g로」).
 * kg·L 은 g·mL 로 바꾼다. 숫자를 못 찾으면 amount·unit 이 null — 화면은 text 만 보인다.
 */
public record ServingSize(String text, BigDecimal amount, String unit) {

    public static final String GRAM = "g";
    public static final String MILLILITER = "mL";

    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l)(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public static ServingSize parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        Matcher m = AMOUNT.matcher(text.replaceAll("(?<=\\d),(?=\\d{3})", ""));   // 1,000mL
        if (!m.find()) return new ServingSize(text, null, null);
        BigDecimal n = new BigDecimal(m.group(1));
        return switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "kg" -> new ServingSize(text, plain(n.multiply(THOUSAND)), GRAM);
            case "l"  -> new ServingSize(text, plain(n.multiply(THOUSAND)), MILLILITER);
            case "ml" -> new ServingSize(text, plain(n), MILLILITER);
            default   -> new ServingSize(text, plain(n), GRAM);
        };
    }

    private static BigDecimal plain(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;      // 3E+2 가 아니라 300
    }

    /** 100g 기준 값의 이름 — mL 제품은 100mL. 제공량을 모르면 100g. */
    public static String basisOf(ServingSize s) {
        return s != null && MILLILITER.equals(s.unit()) ? "100mL" : "100g";
    }
}
