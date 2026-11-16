package com.example.nutriuniv.domain.product.util;

import com.example.nutriuniv.common.util.CsvReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashCalculatorTest {

    /** 데이터 팀 product_master.csv 앞부분 99행 (2026-11-04 build.py 결과). */
    private static final String SAMPLE = "/product/product_master_sample.csv";

    private static List<Map<String, String>> rows;

    @BeforeAll
    static void loadSample() throws Exception {
        try (Reader r = new InputStreamReader(
                Objects.requireNonNull(ContentHashCalculatorTest.class.getResourceAsStream(SAMPLE)), StandardCharsets.UTF_8)) {
            List<CsvReader.Record> records = CsvReader.read(r);
            List<String> header = records.get(0).fields();
            rows = records.subList(1, records.size()).stream().map(rec -> {
                Map<String, String> m = new HashMap<>();
                for (int i = 0; i < header.size(); i++) m.put(header.get(i).trim(), rec.get(i));
                return m;
            }).toList();
        }
    }

    @Test
    void 샘플_전체가_파일의_content_hash와_같다() {
        assertThat(rows).hasSize(99);
        for (Map<String, String> row : rows) {
            assertThat(ContentHashCalculator.hash(row))
                    .as("master_id=%s", row.get("master_id"))
                    .isEqualTo(row.get("content_hash"));
        }
    }

    @Test
    void 숫자를_double로_바꿨다가_다시_맞춰도_같은_hash() {
        // 공공 API 수집 쪽은 값을 숫자로 들고 있다가 hash 직전에 문자열로 맞춘다 — 그 경로를 흉내 낸다
        List<String> numeric = ContentHashCalculator.HASH_COLUMNS.stream()
                .filter(c -> ContentHashCalculator.NUTRIENT_COLUMNS.stream().anyMatch(c::startsWith))
                .toList();
        assertThat(numeric).hasSize(20);
        for (Map<String, String> row : rows) {
            Map<String, String> typed = new HashMap<>(row);
            for (String c : numeric) {
                String v = row.get(c);
                typed.put(c, ContentHashCalculator.formatNumber(v.isEmpty() ? null : Double.parseDouble(v)));
            }
            typed.put("is_discontinued", ContentHashCalculator.formatBoolean(Boolean.parseBoolean(row.get("is_discontinued"))));
            assertThat(ContentHashCalculator.hash(typed))
                    .as("master_id=%s", row.get("master_id"))
                    .isEqualTo(row.get("content_hash"));
        }
    }

    @Test
    void 숫자는_소수_둘째_자리로_맞추고_끝의_0과_점을_뗀다() {
        assertThat(ContentHashCalculator.formatNumber(395.20)).isEqualTo("395.2");
        assertThat(ContentHashCalculator.formatNumber(80.00)).isEqualTo("80");
        assertThat(ContentHashCalculator.formatNumber(600.0)).isEqualTo("600");
        assertThat(ContentHashCalculator.formatNumber(446.666666)).isEqualTo("446.67");
        assertThat(ContentHashCalculator.formatNumber(726126.3)).isEqualTo("726126.3");
        assertThat(ContentHashCalculator.formatNumber(0.0)).isEqualTo("0");
    }

    @Test
    void 음수_0은_0() {
        assertThat(ContentHashCalculator.formatNumber(-0.0)).isEqualTo("0");
        assertThat(ContentHashCalculator.formatNumber(-0.001)).isEqualTo("0");
    }

    @Test
    void 경계값은_Python_round와_같다() {
        // Python: round(2.675, 2) = 2.67 (2.675 의 2진 값이 2.67499...), round(0.125, 2) = 0.12 (짝수 쪽), round(0.375, 2) = 0.38
        assertThat(ContentHashCalculator.formatNumber(2.675)).isEqualTo("2.67");
        assertThat(ContentHashCalculator.formatNumber(0.125)).isEqualTo("0.12");
        assertThat(ContentHashCalculator.formatNumber(0.375)).isEqualTo("0.38");
    }

    @Test
    void BigDecimal_입력도_double_2진_값_기준() {
        assertThat(ContentHashCalculator.formatNumber(new java.math.BigDecimal("2.675"))).isEqualTo("2.67");
        assertThat(ContentHashCalculator.formatNumber((java.math.BigDecimal) null)).isEmpty();
    }

    @Test
    void null은_빈_문자열() {
        assertThat(ContentHashCalculator.formatNumber((Double) null)).isEmpty();
        assertThat(ContentHashCalculator.formatBoolean(null)).isEmpty();
    }

    @Test
    void 참거짓은_소문자() {
        assertThat(ContentHashCalculator.formatBoolean(true)).isEqualTo("true");
        assertThat(ContentHashCalculator.formatBoolean(false)).isEqualTo("false");
    }

    @Test
    void 대상은_30칸이고_순서가_고정이다() {
        List<String> cols = ContentHashCalculator.HASH_COLUMNS;
        assertThat(cols).hasSize(30);
        assertThat(cols.get(0)).isEqualTo("barcode");
        assertThat(cols.get(8)).isEqualTo("serving_size");
        assertThat(cols.get(9)).isEqualTo("calories_kcal");
        assertThat(cols.get(19)).isEqualTo("calories_kcal_per_100g");
        assertThat(cols.get(29)).isEqualTo("is_discontinued");
        assertThat(cols).doesNotContain("master_id", "content_hash", "created_at", "updated_at");
    }

    @Test
    void 대상_밖_컬럼은_hash에_영향이_없다() {
        Map<String, String> row = new HashMap<>(rows.get(0));
        String before = ContentHashCalculator.hash(row);
        row.put("master_id", "다른값");
        row.put("updated_at", "2099-01-01T00:00:00");
        assertThat(ContentHashCalculator.hash(row)).isEqualTo(before);
    }

    @Test
    void 대상_칸이_하나라도_바뀌면_hash가_달라진다() {
        Map<String, String> row = new HashMap<>(rows.get(0));
        String before = ContentHashCalculator.hash(row);
        row.put("sodium_mg", "1341");
        assertThat(ContentHashCalculator.hash(row)).isNotEqualTo(before);
    }

    @Test
    void 없는_컬럼과_null은_빈_문자열로_친다() {
        Map<String, String> withNull = new HashMap<>(rows.get(0));
        withNull.put("brand", null);
        Map<String, String> withEmpty = new HashMap<>(rows.get(0));
        withEmpty.put("brand", "");
        Map<String, String> missing = new HashMap<>(rows.get(0));
        missing.remove("brand");
        String expected = ContentHashCalculator.hash(withEmpty);
        assertThat(ContentHashCalculator.hash(withNull)).isEqualTo(expected);
        assertThat(ContentHashCalculator.hash(missing)).isEqualTo(expected);
    }
}