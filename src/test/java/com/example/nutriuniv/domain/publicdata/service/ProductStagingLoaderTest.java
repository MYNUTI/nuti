package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.publicdata.dto.StagingLoadResponse;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository;
import com.example.nutriuniv.domain.product.util.ContentHashCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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

    // ── 서버 디스크 파일 (CSV 수신함) ──

    @Test
    void 디스크_파일로_복제_테이블을_통째로_채운다(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("product_master_20261123.csv");
        String csv = "﻿" + String.join(",", header()) + "\r\n" + row("A", "8809665551047", "가", "") + "\r\n"
                + row("B", "8801007965697", "나", "") + "\r\n";   // 엑셀식 BOM · CRLF 도 읽는다
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        ProductStagingRepository repo = mock(ProductStagingRepository.class);
        ProductLoadService loadService = mock(ProductLoadService.class);
        ProductStagingLoader loader = new ProductStagingLoader(repo, loadService, mock(PlatformTransactionManager.class));

        StagingLoadResponse r = loader.load(file);

        assertEquals(2, r.stagedCount());
        assertEquals(0, r.hashMismatchCount());
        verify(repo).truncate();
        verify(repo).insertAll(argThat(rows -> rows.size() == 2 && "가".equals(rows.get(0).get("product_name"))), any());
    }

    @Test
    void 디스크_파일이_UTF8이_아니면_400이고_복제_테이블은_그대로(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("product_master_20261123.csv");
        String csv = String.join(",", header()) + "\nA,가\n";
        Files.write(file, csv.getBytes(java.nio.charset.Charset.forName("MS949")));
        ProductStagingRepository repo = mock(ProductStagingRepository.class);
        ProductStagingLoader loader = new ProductStagingLoader(repo, mock(ProductLoadService.class), mock(PlatformTransactionManager.class));

        CustomException e = assertThrows(CustomException.class, () -> loader.load(file));

        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        verify(repo, never()).truncate();
    }

    @Test
    void 디스크_파일이_없으면_404(@TempDir Path dir) {
        ProductStagingLoader loader = new ProductStagingLoader(mock(ProductStagingRepository.class),
                mock(ProductLoadService.class), mock(PlatformTransactionManager.class));

        CustomException e = assertThrows(CustomException.class, () -> loader.load(dir.resolve("없음.csv")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void 칩_열이_없으면_행에_칩_값이_없다() {
        ProductStagingLoader.Parsed p = ProductStagingLoader.parse(read(String.join(",", header()) + "\n" + row("A", "", "가", "")));
        assertFalse(p.rows().get(0).containsKey(ProductStagingRepository.EXTRA_CATEGORIES));   // 반영 때 기존 칩 그대로
    }

    @Test
    void 칩_열이_있으면_정리해서_넣고_hash에는_넣지_않는다() {
        List<String> cols = new java.util.ArrayList<>(header());
        cols.add("extra_categories");
        String csv = String.join(",", cols) + "\n"
                + withCategory(row("A", "", "가", ""), "0505") + ",\"210;0505|0201 0201\"\n"
                + withCategory(row("B", "", "나", ""), "0505") + ",\n";
        ProductStagingLoader.Parsed p = ProductStagingLoader.parse(read(csv));

        assertEquals("0201|0210", p.rows().get(0).get(ProductStagingRepository.EXTRA_CATEGORIES));   // 앞 0 · 소속 뺌 · 중복 · 정렬
        assertEquals("", p.rows().get(1).get(ProductStagingRepository.EXTRA_CATEGORIES));            // 열은 있고 칸이 빔 = 칩 없음
        // 칩만 달라도 hash 는 같다
        assertEquals(p.rows().get(0).get("content_hash"),
                ProductStagingLoader.parse(read(String.join(",", header()) + "\n" + withCategory(row("A", "", "가", ""), "0505")))
                        .rows().get(0).get("content_hash"));
    }

    @Test
    void 칩_값_정리_규칙() {
        assertEquals("", ProductStagingLoader.normalizeChips("", "0505"));
        assertEquals("", ProductStagingLoader.normalizeChips(null, null));
        assertEquals("", ProductStagingLoader.normalizeChips("505", "0505"));
        assertEquals("0210", ProductStagingLoader.normalizeChips(" 0210 | 210 ", ""));
        assertEquals("0201|0210", ProductStagingLoader.normalizeChips("0210,0201", "505"));
    }

    // ── 도우미 ──

    /** row() 로 만든 행의 category 칸을 바꾼다. */
    private static String withCategory(String line, String category) {
        List<String> cols = header();
        String[] cells = line.split(",", -1);
        cells[cols.indexOf("category")] = category;
        return String.join(",", cells);
    }

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