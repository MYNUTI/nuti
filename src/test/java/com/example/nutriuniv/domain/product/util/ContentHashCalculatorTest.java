package com.example.nutriuniv.domain.product.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashCalculatorTest {

    @Test
    void 파이프라인_build_py와_같은_hash를_낸다() {
        assertThat(ContentHashCalculator.hash(ProductMasterSample.row())).isEqualTo(ProductMasterSample.BUILD_PY_HASH);
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
        Map<String, String> row = ProductMasterSample.row();
        String before = ContentHashCalculator.hash(row);
        row.put("master_id", "다른값");
        row.put("updated_at", "2099-01-01T00:00:00");
        row.put("extra_categories", "0210");
        assertThat(ContentHashCalculator.hash(row)).isEqualTo(before);
    }

    @Test
    void 대상_칸이_하나라도_바뀌면_hash가_달라진다() {
        Map<String, String> row = ProductMasterSample.row();
        String before = ContentHashCalculator.hash(row);
        row.put("sodium_mg", "1341");
        assertThat(ContentHashCalculator.hash(row)).isNotEqualTo(before);
    }

    @Test
    void 없는_컬럼과_null은_빈_문자열로_친다() {
        Map<String, String> withNull = ProductMasterSample.row();
        withNull.put("brand", null);
        Map<String, String> withEmpty = ProductMasterSample.row();
        withEmpty.put("brand", "");
        Map<String, String> missing = new HashMap<>(ProductMasterSample.row());
        missing.remove("brand");
        String expected = ContentHashCalculator.hash(withEmpty);
        assertThat(ContentHashCalculator.hash(withNull)).isEqualTo(expected);
        assertThat(ContentHashCalculator.hash(missing)).isEqualTo(expected);
    }
}
