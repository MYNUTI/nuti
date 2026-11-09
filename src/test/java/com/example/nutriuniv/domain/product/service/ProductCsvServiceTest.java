package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.product.dto.ProductUploadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 적재 파일 단위 (v3 DATA_000) — 필수 열 · master_id 묶기 · 실패 행 · 인코딩 · 적재 기록. */
@ExtendWith(MockitoExtension.class)
class ProductCsvServiceTest {

    private static final String HEADER = String.join(",", headerColumns());

    @Mock ProductCsvRowService rowService;
    @Mock ProductLoadService loadService;
    @Mock CategoryRepository categoryRepository;

    @InjectMocks ProductCsvService service;

    @Test
    void 같은_master_id_행은_한_제품으로_묶고_바코드를_모은다() throws Exception {
        String csv = HEADER + "\n" + line("A", "8809665551047", "가") + "\n" + line("B", "", "나") + "\n" + line("A", "08801007965697", "가");
        List<CsvReader.Record> recs = CsvReader.read(new StringReader(csv));
        List<ProductUploadResponse.FailItem> fails = new ArrayList<>();

        List<ProductCsvRowService.LoadItem> items = ProductCsvService.group(recs, ProductCsvService.header(recs.get(0)), fails);

        assertEquals(2, items.size());
        assertEquals("A", items.get(0).masterId());
        assertEquals(List.of("8809665551047", "08801007965697"), items.get(0).rawBarcodes());
        assertEquals(2, items.get(0).line());
        assertTrue(fails.isEmpty());
    }

    @Test
    void 필수_열이_없으면_아무것도_넣지_않고_422() {
        MockMultipartFile f = file("master_id,product_name\nA,가\n", StandardCharsets.UTF_8);
        CustomException e = assertThrows(CustomException.class, () -> service.upload(f));
        assertTrue(e.getMessage().contains("barcode"));
        verifyNoInteractions(loadService, rowService);
    }

    @Test
    void UTF8_이_아니면_400() {
        MockMultipartFile f = file(HEADER + "\n" + line("A", "", "사천짜볶이"), Charset.forName("MS949"));
        assertThrows(CustomException.class, () -> service.upload(f));
        verifyNoInteractions(loadService, rowService);
    }

    @Test
    void csv_가_아니면_415() {
        MockMultipartFile f = new MockMultipartFile("file", "products.xlsx", "application/octet-stream", new byte[]{1});
        assertThrows(CustomException.class, () -> service.upload(f));
    }

    @Test
    void 제품별_결과를_세고_실패는_줄_번호와_함께_돌려준다() {
        when(loadService.start("products.csv")).thenReturn(7L);
        when(categoryRepository.findAllByCodeIsNotNull()).thenReturn(List.of());
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("A")), anyMap(), anyMap(), eq(false)))
                .thenReturn(ProductCsvRowService.Outcome.CREATED);
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("B")), anyMap(), anyMap(), eq(false)))
                .thenThrow(new IllegalArgumentException("없는 중분류 코드입니다: 9999"));
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("C")), anyMap(), anyMap(), eq(false)))
                .thenReturn(ProductCsvRowService.Outcome.UNCHANGED);

        String csv = HEADER + "\n" + line("A", "", "가") + "\n" + line("B", "", "나") + "\n" + line("", "", "이름만") + "\n" + line("C", "", "다");
        ProductUploadResponse r = service.upload(file(csv, StandardCharsets.UTF_8));

        assertEquals(7L, r.getLoadId());
        assertEquals(4, r.getTotalCount());
        assertEquals(2, r.getSuccessCount());
        assertEquals(1, r.getCreatedCount());
        assertEquals(1, r.getUnchangedCount());
        assertEquals(2, r.getFailCount());
        assertEquals(3, r.getFailItems().get(0).getRowNumber());   // B
        assertEquals("B", r.getFailItems().get(0).getMasterId());
        assertEquals(4, r.getFailItems().get(1).getRowNumber());   // master_id 빈 행
        verify(loadService).finish(7L, 4, 1, 0, 1, 0, 2);
    }

    // ── 도우미 ──

    private static List<String> headerColumns() {
        List<String> cols = new ArrayList<>(ProductCsvService.REQUIRED_COLUMNS);
        cols.addAll(List.of("source", "maker", "ingredients", "serving_size", "created_at", "updated_at"));
        return cols;
    }

    /** 헤더 순서대로 master_id · barcode · product_name 만 채운 행. */
    private static String line(String masterId, String barcode, String name) {
        Map<String, String> v = Map.of("master_id", masterId, "barcode", barcode, "product_name", name);
        return String.join(",", headerColumns().stream().map(c -> v.getOrDefault(c, "")).toList());
    }

    private static MockMultipartFile file(String content, Charset cs) {
        return new MockMultipartFile("file", "products.csv", "text/csv", content.getBytes(cs));
    }
}
