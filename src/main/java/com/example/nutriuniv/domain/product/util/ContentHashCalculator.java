package com.example.nutriuniv.domain.product.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 제품 34컬럼의 content_hash — 데이터 팀 build.py 와 같은 값을 낸다. 같으면 그 제품은 바뀌지 않은 것이다.
 * <ul>
 *   <li>대상: barcode 부터 is_discontinued 까지 30칸 (master_id, content_hash, created_at, updated_at 제외), {@link #HASH_COLUMNS} 순서</li>
 *   <li>30칸을 0x1F(Unit Separator)로 이어 붙여 UTF-8 sha256 hex. null 은 빈 문자열</li>
 *   <li>숫자: 소수 둘째 자리로 맞춘 뒤 끝의 0과 점을 뗀다 (395.20 → 395.2, 80.00 → 80, -0 → 0)</li>
 *   <li>참/거짓: true / false (소문자)</li>
 * </ul>
 * <b>반올림은 HALF_EVEN, 입력은 double 의 정확한 2진 값.</b> build.py 는 Python round(x, 2)를 쓰는데, 이것은 반올림이 아니라
 * float 의 실제 2진 값을 짝수 쪽으로 맞춘다(round(2.675, 2) = 2.67). {@code new BigDecimal(double)} + HALF_EVEN 이 같은 결과를 낸다.
 * {@code BigDecimal.valueOf(double)}이나 HALF_UP 을 쓰면 .xx5 경계 값이 달라져 바뀌지 않은 제품이 「바뀜」으로 잡힌다.
 * 순수 함수 — 테스트(ContentHashCalculatorTest)로 고정.
 */
public final class ContentHashCalculator {

    /** 영양성분 10종 — 34컬럼 순서 (열량 · 나트륨 · 탄수화물 · 당류 · 지방 · 트랜스지방 · 포화지방 · 콜레스테롤 · 식이섬유 · 단백질). */
    public static final List<String> NUTRIENT_COLUMNS = List.of(
            "calories_kcal", "sodium_mg", "carbohydrate_g", "sugar_g", "fat_g",
            "trans_fat_g", "saturated_fat_g", "cholesterol_mg", "dietary_fiber_g", "protein_g");

    /** hash 대상 30칸 — 이 순서 그대로 이어 붙인다. */
    public static final List<String> HASH_COLUMNS;
    static {
        java.util.ArrayList<String> cols = new java.util.ArrayList<>(List.of(
                "barcode", "report_no", "source", "product_name", "brand", "category", "maker", "ingredients", "serving_size"));
        cols.addAll(NUTRIENT_COLUMNS);
        NUTRIENT_COLUMNS.forEach(c -> cols.add(c + "_per_100g"));
        cols.add("is_discontinued");
        HASH_COLUMNS = List.copyOf(cols);
    }

    private static final String SEPARATOR = "\u001f";

    private ContentHashCalculator() {}

    /**
     * 컬럼명 → 문자열 값(이미 {@link #formatNumber}·{@link #formatBoolean} 으로 맞춘 값)으로 hash 를 낸다.
     * 없는 컬럼과 null 은 빈 문자열. 적재 CSV 한 줄을 그대로 넣어도 된다.
     */
    public static String hash(Map<String, String> row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HASH_COLUMNS.size(); i++) {
            if (i > 0) sb.append(SEPARATOR);
            String v = row.get(HASH_COLUMNS.get(i));
            if (v != null) sb.append(v);
        }
        return sha256Hex(sb.toString());
    }

    /** 숫자 → hash 용 문자열. null 은 빈 문자열. 소수 둘째 자리 HALF_EVEN, 끝의 0·점 제거, 지수 표기 없음. */
    public static String formatNumber(Double value) {
        if (value == null) return "";
        if (value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("숫자가 아닙니다: " + value);
        }
        BigDecimal rounded = new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN);
        if (rounded.signum() == 0) return "0";                        // -0.001 → -0.00 → 0
        return rounded.stripTrailingZeros().toPlainString();          // 600.00 → 600, 395.20 → 395.2
    }

    /** BigDecimal 로 들고 있는 값용 — double 로 바꾼 뒤 같은 규칙. build.py 가 float 로 계산하므로 2진 값 기준으로 맞춘다. */
    public static String formatNumber(BigDecimal value) {
        return value == null ? "" : formatNumber(value.doubleValue());
    }

    /** 참/거짓 → true / false. null 은 빈 문자열. */
    public static String formatBoolean(Boolean value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);   // 모든 JVM 에 있어 실제로는 일어나지 않는다
        }
    }
}