package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.grade.service.GradeBatchService;
import com.example.nutriuniv.domain.product.service.ProductCsvRowService;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository.ChangedRow;
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

/** 복제 테이블 → 실제 product 반영 — 후보만 넘김 · 다시 살림 · 사라진 제품 비활성 · 대량 비활성 방지 · 실패 집계. */
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
        when(stagingRepository.findDisappearedProductIds()).thenReturn(List.of());
        when(stagingRepository.countActiveLoadedProducts()).thenReturn(100L);
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
        when(stagingRepository.findChanged()).thenReturn(List.of(
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
    void 최신화가_비활성으로_돌렸던_제품이_다시_나타나면_살린다() {
        when(stagingRepository.count()).thenReturn(1L);
        when(stagingRepository.findChanged()).thenReturn(List.of(new ChangedRow("A", 5L, true)));
        when(stagingRepository.findRows(anyList())).thenReturn(rows("A"));
        when(rowService.apply(any(), anyMap(), anyMap(), eq(false))).thenReturn(ProductCsvRowService.Outcome.UNCHANGED);
        when(jdbcTemplate.update(contains("SET is_active = TRUE"), eq(5L))).thenReturn(1);

        SyncApplyResponse r = service.apply(false);

        assertEquals(1, r.reactivatedCount());
        verify(jdbcTemplate).update(contains("SYNC_REACTIVATE"), eq(5L));
        verify(gradeBatchService).recomputeAsync(any());
    }

    @Test
    void 사라진_제품은_비활성으로_돌리고_이력을_남긴다() {
        when(stagingRepository.count()).thenReturn(100L);
        when(stagingRepository.findChanged()).thenReturn(List.of());
        when(stagingRepository.findDisappearedProductIds()).thenReturn(List.of(7L, 8L));   // 100개 중 2개 = 2%
        when(jdbcTemplate.update(contains("SET is_active = FALSE"), anyLong())).thenReturn(1);

        SyncApplyResponse r = service.apply(false);

        assertFalse(r.deactivationSkipped());
        assertEquals(2, r.disappearedCount());
        assertEquals(2, r.deactivatedCount());
        verify(jdbcTemplate).update(contains("SYNC_DEACTIVATE"), eq(7L));
        verify(jdbcTemplate).update(contains("SYNC_DEACTIVATE"), eq(8L));
        verify(gradeBatchService).recomputeAsync(any());
    }

    @Test
    void 사라진_제품이_10퍼센트를_넘으면_비활성을_건너뛴다() {
        when(stagingRepository.count()).thenReturn(80L);
        when(stagingRepository.findChanged()).thenReturn(List.of());
        when(stagingRepository.findDisappearedProductIds()).thenReturn(ids(20));          // 100개 중 20개 = 20%

        SyncApplyResponse r = service.apply(false);

        assertTrue(r.deactivationSkipped());
        assertEquals(20, r.disappearedCount());
        assertEquals(0, r.deactivatedCount());
        verify(jdbcTemplate, never()).update(contains("SET is_active = FALSE"), anyLong());
        verify(gradeBatchService, never()).recomputeAsync(any());   // 바뀐 것이 없다
    }

    @Test
    void 강제하면_10퍼센트를_넘어도_비활성으로_돌린다() {
        when(stagingRepository.count()).thenReturn(80L);
        when(stagingRepository.findChanged()).thenReturn(List.of());
        when(stagingRepository.findDisappearedProductIds()).thenReturn(ids(20));
        when(jdbcTemplate.update(contains("SET is_active = FALSE"), anyLong())).thenReturn(1);

        SyncApplyResponse r = service.apply(true);

        assertFalse(r.deactivationSkipped());
        assertEquals(20, r.deactivatedCount());
    }

    @Test
    void 한_제품이_실패해도_나머지는_반영하고_실패로_센다() {
        when(stagingRepository.count()).thenReturn(2L);
        when(stagingRepository.findChanged()).thenReturn(List.of(new ChangedRow("A", null, false), new ChangedRow("B", null, false)));
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

    private static List<Long> ids(int n) {
        List<Long> out = new ArrayList<>();
        for (long i = 1; i <= n; i++) out.add(i);
        return out;
    }
}