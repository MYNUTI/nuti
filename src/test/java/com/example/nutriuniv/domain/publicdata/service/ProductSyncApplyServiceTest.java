package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.grade.service.GradeBatchService;
import com.example.nutriuniv.domain.product.service.ProductCsvRowService;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.publicdata.dto.SourceDeactivation;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncPreviewResponse;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository.ChangedRow;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository.DisappearedRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 복제 테이블 → 실제 product 반영 — 후보만 넘김 · 다시 살림 · 출처별 사라진 제품 비활성 · 출처별 대량 비활성 방지 · 실패 집계. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSyncApplyServiceTest {

    @Mock ProductStagingRepository stagingRepository;
    @Mock ProductCsvRowService rowService;
    @Mock ProductLoadService loadService;
    @Mock CategoryRepository categoryRepository;
    @Mock GradeBatchService gradeBatchService;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock PlatformTransactionManager transactionManager;

    ProductSyncApplyService service;

    @BeforeEach
    void setUp() {
        service = new ProductSyncApplyService(stagingRepository, rowService, loadService, categoryRepository,
                gradeBatchService, jdbcTemplate, transactionManager);
        when(loadService.start(ProductSyncApplyService.LOAD_NAME)).thenReturn(9L);
        when(categoryRepository.findAllByCodeIsNotNull()).thenReturn(List.of());
        when(stagingRepository.findDisappeared()).thenReturn(List.of());
        when(stagingRepository.countActiveLoadedBySource()).thenReturn(Map.of("nutri_db", 100L));
    }

    @Test
    void 복제_테이블이_비면_409이고_아무것도_하지_않는다() {
        when(stagingRepository.count()).thenReturn(0L);

        assertThrows(CustomException.class, () -> service.apply(false));
        verify(loadService, never()).start(any());
        verifyNoInteractions(rowService, gradeBatchService);
    }

    @Test
    void 후보만_적재_로직에_넘기고_건수를_센다() {
        when(stagingRepository.count()).thenReturn(10L);
        when(stagingRepository.findChangedPage(eq(""), anyInt())).thenReturn(List.of(
                new ChangedRow("A", null, false), new ChangedRow("B", 2L, false), new ChangedRow("C", 3L, false)));
        when(stagingRepository.findRows(anyList())).thenReturn(rows("A", "B", "C"));
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("A")), anyMap(), anyMap(), eq(false)))
                .thenReturn(ProductCsvRowService.Outcome.CREATED);
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("B")), anyMap(), anyMap(), eq(false)))
                .thenReturn(ProductCsvRowService.Outcome.UPDATED);
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("C")), anyMap(), anyMap(), eq(false)))
                .thenReturn(ProductCsvRowService.Outcome.PROTECTED);

        SyncApplyResponse r = service.apply(false);

        assertEquals(9L, r.loadId());
        assertEquals(3, r.candidateCount());
        assertEquals(1, r.createdCount());
        assertEquals(1, r.updatedCount());
        assertEquals(1, r.protectedCount());
        assertEquals(7, r.unchangedCount());                   // 10 − 처리 3
        verify(loadService).finish(9L, 10, 1, 1, 7, 1, 0);
        verify(gradeBatchService).recomputeAsync(ProductSyncApplyService.LOAD_NAME);

        ArgumentCaptor<ProductCsvRowService.LoadItem> item = ArgumentCaptor.forClass(ProductCsvRowService.LoadItem.class);
        verify(rowService, times(3)).apply(item.capture(), anyMap(), anyMap(), eq(false));
        assertEquals(List.of("088000000000A"), item.getAllValues().get(0).rawBarcodes());   // 복제 테이블 barcode 그대로
    }

    @Test
    void 칩_열이_있는_행은_칩까지_반영하고_칩만_바뀌면_수정으로_센다() {
        when(stagingRepository.count()).thenReturn(5L);
        when(stagingRepository.findChangedPage(eq(""), anyInt())).thenReturn(List.of(new ChangedRow("A", 1L, false), new ChangedRow("B", 2L, false)));
        Map<String, Map<String, String>> rows = rows("A", "B");
        rows.get("A").put(ProductStagingRepository.EXTRA_CATEGORIES, "0210");
        rows.get("B").put(ProductStagingRepository.EXTRA_CATEGORIES, "");
        when(stagingRepository.findRows(anyList())).thenReturn(rows);
        when(rowService.apply(any(), anyMap(), anyMap(), eq(true))).thenReturn(ProductCsvRowService.Outcome.UNCHANGED);
        when(rowService.applyChips(eq("A"), eq("0210"), anyMap())).thenReturn(true);    // 칩만 바뀜
        when(rowService.applyChips(eq("B"), eq(""), anyMap())).thenReturn(false);       // 이미 같음

        SyncApplyResponse r = service.apply(false);

        verify(rowService, times(2)).apply(any(), anyMap(), anyMap(), eq(true));
        assertEquals(1, r.updatedCount());
        assertEquals(4, r.unchangedCount());
        verify(gradeBatchService).recomputeAsync(any());
    }

    @Test
    void 칩_열이_없는_행은_칩을_건드리지_않는다() {
        when(stagingRepository.count()).thenReturn(1L);
        when(stagingRepository.findChangedPage(eq(""), anyInt())).thenReturn(List.of(new ChangedRow("A", 1L, false)));
        when(stagingRepository.findRows(anyList())).thenReturn(rows("A"));
        when(rowService.apply(any(), anyMap(), anyMap(), eq(false))).thenReturn(ProductCsvRowService.Outcome.UNCHANGED);

        SyncApplyResponse r = service.apply(false);

        verify(rowService).apply(any(), anyMap(), anyMap(), eq(false));
        verify(rowService, never()).applyChips(any(), any(), anyMap());
        assertEquals(0, r.updatedCount());
    }

    @Test
    void 현행_아님이던_제품이_다시_나타나면_현행으로_되돌린다() {
        when(stagingRepository.count()).thenReturn(1L);
        when(stagingRepository.findChangedPage(eq(""), anyInt())).thenReturn(List.of(new ChangedRow("A", 5L, true)));
        when(stagingRepository.findRows(anyList())).thenReturn(rows("A"));
        when(rowService.apply(any(), anyMap(), anyMap(), eq(false))).thenReturn(ProductCsvRowService.Outcome.UNCHANGED);
        when(jdbcTemplate.update(contains("SET is_current = TRUE"), eq(5L))).thenReturn(1);

        SyncApplyResponse r = service.apply(false);

        assertEquals(1, r.reactivatedCount());
        verify(jdbcTemplate).update(contains("SYNC_REACTIVATE"), eq(5L));
        verify(gradeBatchService).recomputeAsync(any());
    }

    @Test
    void 사라진_제품은_끄지_않고_현행_아님으로_돌리고_이력을_남긴다() {
        when(stagingRepository.count()).thenReturn(100L);
        when(stagingRepository.findDisappeared()).thenReturn(gone("nutri_db", 7L, 8L));   // 100개 중 2개 = 2%
        when(jdbcTemplate.update(contains("SET is_current = FALSE"), anyLong())).thenReturn(1);

        SyncApplyResponse r = service.apply(false);

        assertFalse(r.deactivationSkipped());
        assertEquals(2, r.disappearedCount());
        assertEquals(2, r.deactivatedCount());
        assertEquals(List.of(new SourceDeactivation("nutri_db", 100, 2, 2, false)), r.sources());
        verify(jdbcTemplate).update(contains("SYNC_DEACTIVATE"), eq(7L));
        verify(jdbcTemplate).update(contains("SYNC_DEACTIVATE"), eq(8L));
        verify(gradeBatchService).recomputeAsync(any());
    }

    @Test
    void 출처마다_10퍼센트를_따로_검사해_넘는_출처만_건너뛴다() {
        // 전체로 보면 22/1100 = 2% 라 안 걸리지만, foodqr 은 20/100 = 20% 라 그 출처만 건너뛴다
        when(stagingRepository.count()).thenReturn(1080L);
        when(stagingRepository.countActiveLoadedBySource()).thenReturn(Map.of("nutri_db", 1000L, "foodqr", 100L));
        List<DisappearedRow> rows = new ArrayList<>(gone("nutri_db", 1L, 2L));
        for (long id = 101; id <= 120; id++) rows.add(new DisappearedRow(id, "foodqr"));
        when(stagingRepository.findDisappeared()).thenReturn(rows);
        when(jdbcTemplate.update(contains("SET is_current = FALSE"), anyLong())).thenReturn(1);

        SyncApplyResponse r = service.apply(false);

        assertTrue(r.deactivationSkipped());
        assertEquals(22, r.disappearedCount());
        assertEquals(2, r.deactivatedCount());                 // nutri_db 2건만
        assertEquals(List.of(
                new SourceDeactivation("foodqr", 100, 20, 0, true),
                new SourceDeactivation("nutri_db", 1000, 2, 2, false)), r.sources());   // 출처 이름순
        verify(jdbcTemplate, never()).update(contains("SET is_current = FALSE"), eq(101L));
        verify(jdbcTemplate).update(contains("SET is_current = FALSE"), eq(1L));
    }

    @Test
    void 강제하면_10퍼센트를_넘어도_현행_아님으로_돌린다() {
        when(stagingRepository.count()).thenReturn(80L);
        when(stagingRepository.findDisappeared()).thenReturn(gone("nutri_db", ids(20)));           // 100개 중 20개 = 20%
        when(jdbcTemplate.update(contains("SET is_current = FALSE"), anyLong())).thenReturn(1);

        SyncApplyResponse r = service.apply(true);

        assertFalse(r.deactivationSkipped());
        assertEquals(20, r.deactivatedCount());
    }

    @Test
    void 강제하지_않으면_10퍼센트를_넘는_출처는_건너뛰고_등급도_다시_계산하지_않는다() {
        when(stagingRepository.count()).thenReturn(80L);
        when(stagingRepository.findDisappeared()).thenReturn(gone("nutri_db", ids(20)));

        SyncApplyResponse r = service.apply(false);

        assertTrue(r.deactivationSkipped());
        assertEquals(0, r.deactivatedCount());
        verify(jdbcTemplate, never()).update(contains("SET is_current = FALSE"), anyLong());
        verify(gradeBatchService, never()).recomputeAsync(any());   // 바뀐 것이 없다
    }

    @Test
    void 대기_테이블이_맡지_않는_출처는_결과에_나오지_않는다() {
        // 저장소 쿼리가 대기 테이블에 있는 출처만 돌려준다 — 올리브영은 대상도 결과도 아니다
        when(stagingRepository.count()).thenReturn(100L);
        when(stagingRepository.countActiveLoadedBySource()).thenReturn(Map.of("nutri_db", 100L, "foodqr", 50L));

        SyncApplyResponse r = service.apply(false);

        assertEquals(List.of("foodqr", "nutri_db"), r.sources().stream().map(SourceDeactivation::source).toList());
        assertEquals(0, r.deactivatedCount());
    }

    @Test
    void 미리_보기도_출처별로_센다() {
        when(stagingRepository.count()).thenReturn(1080L);
        when(stagingRepository.countChanged()).thenReturn(new ProductStagingRepository.ChangedCounts(1, 1, 0));
        when(stagingRepository.countActiveLoadedBySource()).thenReturn(Map.of("nutri_db", 1000L, "foodqr", 100L));
        List<DisappearedRow> rows = new ArrayList<>(gone("nutri_db", 1L));
        for (long id = 101; id <= 120; id++) rows.add(new DisappearedRow(id, "foodqr"));
        when(stagingRepository.findDisappeared()).thenReturn(rows);

        SyncPreviewResponse p = service.preview();

        assertEquals(1, p.createCount());
        assertEquals(21, p.disappearedCount());
        assertEquals(1100, p.activeLoadedCount());
        assertTrue(p.massDeactivation());
        assertEquals(List.of(
                new SourceDeactivation("foodqr", 100, 20, 0, true),
                new SourceDeactivation("nutri_db", 1000, 1, 0, false)), p.sources());
        verifyNoInteractions(rowService, loadService, gradeBatchService);
    }

    @Test
    void 한_제품이_실패해도_나머지는_반영하고_실패로_센다() {
        when(stagingRepository.count()).thenReturn(2L);
        when(stagingRepository.findChangedPage(eq(""), anyInt())).thenReturn(List.of(new ChangedRow("A", null, false), new ChangedRow("B", null, false)));
        when(stagingRepository.findRows(anyList())).thenReturn(rows("A", "B"));
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("A")), anyMap(), anyMap(), eq(false)))
                .thenThrow(new IllegalArgumentException("없는 중분류 코드입니다: 9999"));
        when(rowService.apply(argThat(i -> i != null && i.masterId().equals("B")), anyMap(), anyMap(), eq(false)))
                .thenReturn(ProductCsvRowService.Outcome.CREATED);

        SyncApplyResponse r = service.apply(false);

        assertEquals(1, r.failCount());
        assertEquals("A", r.failItems().get(0).masterId());
        assertEquals(1, r.createdCount());
        verify(loadService).finish(9L, 2, 1, 0, 0, 0, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 바뀐_행은_한_쪽씩_읽고_다음_쪽은_앞_쪽_마지막_master_id_뒤부터() {
        when(stagingRepository.count()).thenReturn(1000L);
        List<ChangedRow> first = new ArrayList<>();
        for (int i = 0; i < ProductSyncApplyService.CHUNK; i++) first.add(new ChangedRow(String.format("A%04d", i), null, false));
        String last = first.get(first.size() - 1).masterId();
        when(stagingRepository.findChangedPage(eq(""), eq(ProductSyncApplyService.CHUNK))).thenReturn(first);
        when(stagingRepository.findChangedPage(eq(last), eq(ProductSyncApplyService.CHUNK))).thenReturn(List.of(new ChangedRow("B", null, false)));
        when(stagingRepository.findRows(anyList())).thenAnswer(inv -> rows(((List<String>) inv.getArgument(0)).toArray(String[]::new)));
        when(rowService.apply(any(), anyMap(), anyMap(), eq(false))).thenReturn(ProductCsvRowService.Outcome.CREATED);

        SyncApplyResponse r = service.apply(false);

        assertEquals(ProductSyncApplyService.CHUNK + 1, r.candidateCount());
        assertEquals(ProductSyncApplyService.CHUNK + 1, r.createdCount());
        verify(stagingRepository, times(2)).findChangedPage(anyString(), anyInt());   // 둘째 쪽이 덜 차서 더 묻지 않는다
    }

    @Test
    void 대량_비활성_기준() {
        assertFalse(ProductSyncApplyService.exceedsMassDeactivation(10, 100));   // 딱 10% 는 넘지 않음
        assertTrue(ProductSyncApplyService.exceedsMassDeactivation(11, 100));
        assertFalse(ProductSyncApplyService.exceedsMassDeactivation(5, 0));      // 적재 제품이 없으면 해당 없음
        assertFalse(ProductSyncApplyService.exceedsMassDeactivation(0, 100));
    }

    // ── 도우미 ──

    private static Map<String, Map<String, String>> rows(String... masterIds) {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (String id : masterIds) {
            Map<String, String> row = new HashMap<>();
            row.put("master_id", id);
            row.put("barcode", "088000000000" + id);
            row.put("product_name", "제품 " + id);
            row.put("content_hash", "hash-" + id);
            out.put(id, row);
        }
        return out;
    }

    private static List<DisappearedRow> gone(String source, Long... productIds) {
        return Arrays.stream(productIds).map(id -> new DisappearedRow(id, source)).toList();
    }

    private static Long[] ids(int n) {
        Long[] out = new Long[n];
        for (int i = 0; i < n; i++) out[i] = (long) (i + 1);
        return out;
    }
}