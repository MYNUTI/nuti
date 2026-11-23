package com.example.nutriuniv.domain.product.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 파이프라인 product_master.csv 첫 행(사천짜볶이) — 테스트용. content_hash 는 build.py 가 낸 값 그대로다.
 * 서버 계산기(ContentHashCalculator)가 같은 값을 내는지 이 한 행으로 맞춰 본다.
 */
public final class ProductMasterSample {

    public static final String MASTER_ID = "FQR-202410221000001859";
    public static final String BUILD_PY_HASH = "f919c1b4b09bd620331ce4553f7594c97251a8ff7e1394c47b2a02146fd50f09";

    private static final String[] NUTRIENTS = {"calories_kcal", "sodium_mg", "carbohydrate_g", "sugar_g", "fat_g",
            "trans_fat_g", "saturated_fat_g", "cholesterol_mg", "dietary_fiber_g", "protein_g"};
    private static final String[] BASE = {"600", "1340", "128", "20", "4.2", "0.2", "0.6", "0", "", "12"};
    private static final String[] PER_100G = {"200", "446.67", "42.67", "6.67", "1.4", "0.07", "0.2", "0", "", "4"};

    private ProductMasterSample() {}

    /** 컬럼명 → 파일에 적힌 문자열 (content_hash · created_at · updated_at 포함). */
    public static Map<String, String> row() {
        Map<String, String> v = new HashMap<>();
        v.put("master_id", MASTER_ID);
        v.put("barcode", "08809665551047");
        v.put("report_no", "20020614179957");
        v.put("source", "foodqr");
        v.put("product_name", "사천짜볶이");
        v.put("brand", "");
        v.put("category", "0505");
        v.put("maker", "(주)아워홈");
        v.put("ingredients", "");
        v.put("serving_size", "300g");
        for (int i = 0; i < NUTRIENTS.length; i++) {
            v.put(NUTRIENTS[i], BASE[i]);
            v.put(NUTRIENTS[i] + "_per_100g", PER_100G[i]);
        }
        v.put("is_discontinued", "true");
        v.put("content_hash", BUILD_PY_HASH);
        v.put("created_at", "2026-11-04T00:00:00");
        v.put("updated_at", "2026-11-04T00:00:00");
        return v;
    }

    /** 34컬럼 파일 모양 — 헤더 + 이 한 행. */
    public static String csv() {
        List<String> header = new ArrayList<>();
        header.add("master_id");
        header.addAll(ContentHashCalculator.HASH_COLUMNS);
        header.addAll(List.of("content_hash", "created_at", "updated_at"));
        Map<String, String> v = row();
        return String.join(",", header) + "\n" + String.join(",", header.stream().map(v::get).toList()) + "\n";
    }
}
