package com.example.nutriuniv.domain.product.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 제품 34컬럼의 content_hash — 파이프라인 build.py 와 같은 값을 낸다. 같으면 그 제품은 바뀌지 않은 것이다.
 * <ul>
 *   <li>대상: barcode 부터 is_discontinued 까지 30칸 (master_id, content_hash, created_at, updated_at 제외), {@link #HASH_COLUMNS} 순서</li>
 *   <li>30칸을 0x1F(Unit Separator)로 이어 붙여 UTF-8 sha256 hex. null 은 빈 문자열</li>
 *   <li>값은 파일에 적힌 문자열 그대로 쓴다 — 숫자 자릿수 · 참거짓 표기는 build.py 가 이미 맞춰서 쓴다</li>
 * </ul>
 * 순수 함수 — 테스트(ContentHashCalculatorTest)로 고정.
 */
public final class ContentHashCalculator {

    /** 영양성분 10종 — 34컬럼 순서 (열량 · 나트륨 · 탄수화물 · 당류 · 지방 · 트랜스지방 · 포화지방 · 콜레스테롤 · 식이섬유 · 단백질). */
    private static final List<String> NUTRIENT_COLUMNS = List.of(
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
     * 컬럼명 → 문자열 값으로 hash 를 낸다. 없는 컬럼과 null 은 빈 문자열. 적재 CSV 한 줄을 그대로 넣어도 된다.
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

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);   // 모든 JVM 에 있어 실제로는 일어나지 않는다
        }
    }
}