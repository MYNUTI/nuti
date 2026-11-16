package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.product.util.ContentHashCalculator;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** 34컬럼 CSV → 복제 테이블 행 (파싱만, DB 없이). */
class ProductStagingLoaderTest {

    /** ContentHashCalculatorTest 와 같은 샘플 (데이터 팀 product_master.csv 앞 99행). */
    private static final String SAMPLE = "/product/product_master_sample.csv";

    @Test
    void 샘플_99행을_그대로_넣고_hash가_파일과_전부_같다() throws Exception {
        ProductStagingLoader.Parsed p = ProductStagingLoader.parse(sample());

        assertEquals(99, p.rows().size());
        assertEquals(0, p.duplicateCount());
        assertEquals(0, p.missingIdCount());
        assertEquals(0, p.hashMismatchCount());
        assertTrue(p.hashMismatchSamples().isEmpty());

        Map<String, String> first = p.rows().get(0);
        assertEquals("FQR-202410221000001859", first.get("master_id"));
        assertEquals("08809665551047", first.get("barcode"));
        assertEquals("446.67", first.get("sodium_mg_per_100g"));       // 숫자는 문자열 그대로
        assertEquals("f919c1b4b09bd620331ce4553f7594c97251a8ff7e1394c47b2a02146fd50f09", first.get("content_hash"));
        assertFalse(first.containsKey("created_at"));                    // 시각 칸은 넣지 않는다
    }

    @Test
    void 파일_hash와_다르면_건수와_예시를_돌려주고_행은_서버_계산값으로_넣는다() {
        String header = String.join(",", header());
        String line = row("A", "8809665551047", "가", "틀린해시");
        ProductStagingLoader.Parsed p = ProductStagingLoader.parse(read(header + "\n" + line));

        assertEquals(1, p.rows().size());
        assertEquals(1, p.hashMismatchCount());
        assertEquals(List.of("A"), p.hashMismatchSamples());
        assertEquals(ContentHashCalculator.hash(p.rows().get(0)), p.rows().get(0).get("content_hash"));
    }

    @Test
    void 같은_master_id는_첫_행만_넣고_빈_master_id는_건너뛴다() {
        String header = String.join(",", header());
        String csv = header + "\n" + row("A", "8809665551047", "첫째", "")
                + "\n" + row("A", "8801007965697", "둘째", "")
                + "\n" + row("", "", "이름만", "");
        ProductStagingLoader.Parsed p = ProductStagingLoader.parse(read(csv));

        assertEquals(1, p.rows().size());
        assertEquals("첫째", p.rows().get(0).get("product_name"));
        assertEquals(1, p.duplicateCount());
        assertEquals(1, p.missingIdCount());
        assertEquals(0, p.hashMismatchCount());                          // 파일 hash 가 비면 대조하지 않는다
    }

    @Test
    void 필수_열이_없으면_422() {
        CustomException e = assertThrows(CustomException.class,
                () -> ProductStagingLoader.parse(read("master_id,product_name\nA,가\n")));
        assertTrue(e.getMessage().contains("barcode"));
    }

    // ── 도우미 ──

    private static List<CsvReader.Record> sample() throws Exception {
        try (Reader r = new InputStreamReader(
                Objects.requireNonNull(ProductStagingLoaderTest.class.getResourceAsStream(SAMPLE)), StandardCharsets.UTF_8)) {
            return CsvReader.read(r);
        }
    }

    private static List<CsvReader.Record> read(String csv) {
        try {
            return CsvReader.read(new StringReader(csv));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> header() {
        List<String> cols = new java.util.ArrayList<>(ProductStagingLoader.REQUIRED_COLUMNS);
        cols.add("content_hash");
        return cols;
    }

    /** master_id · barcode · product_name · content_hash 만 채운 행 (헤더 순서). */
    private static String row(String masterId, String barcode, String name, String hash) {
        Map<String, String> v = Map.of("master_id", masterId, "barcode", barcode, "product_name", name, "content_hash", hash);
        return String.join(",", header().stream().map(c -> v.getOrDefault(c, "")).toList());
    }
}