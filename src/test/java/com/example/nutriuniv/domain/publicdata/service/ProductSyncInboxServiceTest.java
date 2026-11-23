package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.publicdata.dto.InboxListResponse;
import com.example.nutriuniv.domain.publicdata.dto.InboxListResponse.FileStatus;
import com.example.nutriuniv.domain.publicdata.dto.InboxRunResponse;
import com.example.nutriuniv.domain.publicdata.dto.StagingLoadResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import com.example.nutriuniv.domain.publicdata.entity.ProductSyncInboxFile;
import com.example.nutriuniv.domain.publicdata.entity.ProductSyncInboxFile.Status;
import com.example.nutriuniv.domain.publicdata.entity.ProductSyncInboxFile.Trigger;
import com.example.nutriuniv.domain.publicdata.repository.ProductSyncInboxFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CSV 수신함 — 파일 이름 규칙 · 최신 파일 고르기 · 중복 처리 방지 · 처리 결과 이력 · 목록 상태. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSyncInboxServiceTest {

    @TempDir Path dir;

    @Mock ProductStagingLoader stagingLoader;
    @Mock ProductSyncApplyService applyService;
    @Mock ProductLoadService loadService;
    @Mock ProductSyncInboxFileRepository historyRepository;

    ProductSyncInboxService service;

    @BeforeEach
    void setUp() {
        service = new ProductSyncInboxService(dir.toString(), stagingLoader, applyService, loadService, historyRepository);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loadService.isLoading()).thenReturn(false);
    }

    private Path csv(String name) throws Exception {
        return Files.writeString(dir.resolve(name), "master_id\n");
    }

    private static StagingLoadResponse staged(int rows) {
        return new StagingLoadResponse(rows, 0, 0, 2, List.of());
    }

    private static SyncApplyResponse applied() {
        return new SyncApplyResponse(7L, 100, 12, 3, 8, 88, 1, 0, 5, 5, false, List.of(), 0, List.of());
    }

    // ── 파일 이름 ────────────────────────────────────────────────────────────

    @Test
    void 이름_규칙에_맞는_파일만_날짜를_읽는다() {
        assertEquals(Optional.of(LocalDate.of(2026, 11, 23)), ProductSyncInboxService.parseDate("product_master_20261123.csv"));
        assertTrue(ProductSyncInboxService.parseDate("product_master_20261123.csv.tmp").isEmpty());
        assertTrue(ProductSyncInboxService.parseDate("product_master_20261123.tmp").isEmpty());
        assertTrue(ProductSyncInboxService.parseDate("product_master.csv").isEmpty());
        assertTrue(ProductSyncInboxService.parseDate("product_master_2026112.csv").isEmpty());
        assertTrue(ProductSyncInboxService.parseDate("product_master_20261340.csv").isEmpty());   // 없는 날짜
        assertTrue(ProductSyncInboxService.parseDate("product_master_20260230.csv").isEmpty());
        assertTrue(ProductSyncInboxService.parseDate("PRODUCT_MASTER_20261123.CSV").isEmpty());
    }

    @Test
    void 폴더를_읽으면_최신_날짜_먼저이고_규칙_밖_파일은_따로_모은다() throws Exception {
        csv("product_master_20261121.csv");
        csv("product_master_20261123.csv");
        csv("product_master_20261124.csv.tmp");
        csv("memo.txt");

        List<String> ignored = new java.util.ArrayList<>();
        List<ProductSyncInboxService.InboxCsv> files = service.scan(ignored);

        assertEquals(List.of("product_master_20261123.csv", "product_master_20261121.csv"),
                files.stream().map(ProductSyncInboxService.InboxCsv::fileName).toList());
        assertEquals(List.of("memo.txt", "product_master_20261124.csv.tmp"), ignored);
        assertEquals("product_master_20261123.csv", ProductSyncInboxService.newest(files).orElseThrow().fileName());
    }

    // ── 자동 실행 ────────────────────────────────────────────────────────────

    @Test
    void 자동_실행은_파일이_없으면_아무것도_하지_않는다() {
        service.runScheduled();

        verify(historyRepository, never()).save(any());
        verifyNoInteractions(stagingLoader, applyService);
    }

    @Test
    void 자동_실행은_폴더가_없으면_건너뛴다() {
        ProductSyncInboxService noDir = new ProductSyncInboxService(dir.resolve("없음").toString(),
                stagingLoader, applyService, loadService, historyRepository);

        noDir.runScheduled();

        verifyNoInteractions(stagingLoader, applyService);
    }

    @Test
    void 자동_실행은_최신_파일을_이미_반영했으면_건너뛴다() throws Exception {
        csv("product_master_20261123.csv");
        when(historyRepository.existsByFileNameAndStatus("product_master_20261123.csv", Status.DONE)).thenReturn(true);

        service.runScheduled();

        verify(historyRepository, never()).save(any());
        verifyNoInteractions(stagingLoader, applyService);
    }

    @Test
    void 자동_실행은_다른_적재_중이면_건너뛴다() throws Exception {
        csv("product_master_20261123.csv");
        when(loadService.isLoading()).thenReturn(true);

        service.runScheduled();

        verify(historyRepository, never()).save(any());
    }

    @Test
    void 자동_실행은_가장_새_파일_하나만_채우고_반영한다() throws Exception {
        csv("product_master_20261122.csv");
        Path newest = csv("product_master_20261123.csv");
        when(stagingLoader.load(any(Path.class))).thenReturn(staged(100));
        when(applyService.apply(false)).thenReturn(applied());

        service.runScheduled();

        verify(applyService, timeout(3000)).apply(false);
        verify(stagingLoader).load(newest);
        ArgumentCaptor<ProductSyncInboxFile> saved = ArgumentCaptor.forClass(ProductSyncInboxFile.class);
        verify(historyRepository, timeout(3000).times(2)).save(saved.capture());
        ProductSyncInboxFile h = saved.getValue();
        assertEquals("product_master_20261123.csv", h.getFileName());
        assertEquals(Trigger.SCHEDULE, h.getTriggeredBy());
        assertEquals(Status.DONE, h.getStatus());
    }

    // ── 수동 실행 ────────────────────────────────────────────────────────────

    @Test
    void 수동_실행은_이미_반영한_파일이면_409이고_force면_다시_한다() throws Exception {
        csv("product_master_20261123.csv");
        when(historyRepository.existsByFileNameAndStatus("product_master_20261123.csv", Status.DONE)).thenReturn(true);
        when(stagingLoader.load(any(Path.class))).thenReturn(staged(100));
        when(applyService.apply(false)).thenReturn(applied());

        CustomException e = assertThrows(CustomException.class, () -> service.runManual(null, false));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());

        InboxRunResponse r = service.runManual(null, true);
        assertEquals("product_master_20261123.csv", r.fileName());
        assertEquals("PROCESSING", r.status());
        verify(applyService, timeout(3000)).apply(false);
    }

    @Test
    void 수동_실행은_이름을_지정하면_그_파일을_처리한다() throws Exception {
        Path older = csv("product_master_20261122.csv");
        csv("product_master_20261123.csv");
        when(stagingLoader.load(any(Path.class))).thenReturn(staged(100));
        when(applyService.apply(false)).thenReturn(applied());

        service.runManual("product_master_20261122.csv", false);

        verify(applyService, timeout(3000)).apply(false);
        verify(stagingLoader).load(older);
    }

    @Test
    void 수동_실행은_이름_규칙이_틀리면_400_없는_파일이면_404() throws Exception {
        csv("product_master_20261123.csv");

        CustomException bad = assertThrows(CustomException.class, () -> service.runManual("../etc/passwd", false));
        assertEquals(ErrorCode.BAD_REQUEST, bad.getErrorCode());

        CustomException missing = assertThrows(CustomException.class, () -> service.runManual("product_master_20261101.csv", false));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, missing.getErrorCode());
    }

    @Test
    void 수동_실행은_파일이_하나도_없으면_404_다른_적재_중이면_409() throws Exception {
        CustomException none = assertThrows(CustomException.class, () -> service.runManual(null, false));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, none.getErrorCode());

        csv("product_master_20261123.csv");
        when(loadService.isLoading()).thenReturn(true);
        CustomException busy = assertThrows(CustomException.class, () -> service.runManual(null, false));
        assertEquals(ErrorCode.STATE_CONFLICT, busy.getErrorCode());
        verifyNoInteractions(stagingLoader);
    }

    // ── 처리 ─────────────────────────────────────────────────────────────────

    private ProductSyncInboxFile history() {
        return ProductSyncInboxFile.start("product_master_20261123.csv", LocalDate.of(2026, 11, 23), 10L,
                LocalDateTime.now(), Trigger.MANUAL);
    }

    @Test
    void 처리가_끝나면_건수를_이력에_남긴다() {
        when(stagingLoader.load(any(Path.class))).thenReturn(staged(100));
        when(applyService.apply(false)).thenReturn(applied());
        ProductSyncInboxFile h = history();

        service.process(h, dir.resolve("product_master_20261123.csv"));

        assertEquals(Status.DONE, h.getStatus());
        assertNotNull(h.getFinishedAt());
        assertEquals(100, h.getStagedCount());
        assertEquals(2, h.getHashMismatchCount());
        assertEquals(7L, h.getLoadId());
        assertEquals(3, h.getCreatedCount());
        assertEquals(8, h.getUpdatedCount());
        assertEquals(5, h.getDeactivatedCount());
        assertFalse(h.getDeactivationSkipped());
        verify(historyRepository).save(h);
    }

    @Test
    void 반영에서_실패하면_사유와_채운_행_수를_남긴다() {
        when(stagingLoader.load(any(Path.class))).thenReturn(staged(100));
        when(applyService.apply(false)).thenThrow(new CustomException(ErrorCode.STATE_CONFLICT, "다른 제품 적재가 진행 중입니다."));
        ProductSyncInboxFile h = history();

        service.process(h, dir.resolve("product_master_20261123.csv"));

        assertEquals(Status.FAILED, h.getStatus());
        assertEquals(100, h.getStagedCount());
        assertNotNull(h.getFailReason());
        assertNull(h.getCreatedCount());
    }

    @Test
    void 채운_행이_0이면_반영하지_않고_실패로_남긴다() {
        when(stagingLoader.load(any(Path.class))).thenReturn(staged(0));
        ProductSyncInboxFile h = history();

        service.process(h, dir.resolve("product_master_20261123.csv"));

        assertEquals(Status.FAILED, h.getStatus());
        verify(applyService, never()).apply(anyBoolean());
    }

    @Test
    void 파일을_못_읽으면_실패로_남긴다() {
        when(stagingLoader.load(any(Path.class))).thenThrow(new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "필수 열이 없습니다: brand"));
        ProductSyncInboxFile h = history();

        service.process(h, dir.resolve("product_master_20261123.csv"));

        assertEquals(Status.FAILED, h.getStatus());
        assertNull(h.getStagedCount());
        verify(applyService, never()).apply(anyBoolean());
    }

    // ── 목록 ─────────────────────────────────────────────────────────────────

    @Test
    void 목록은_최신_미처리는_PENDING_오래된_미처리는_SUPERSEDED_처리한_파일은_마지막_결과() throws Exception {
        csv("product_master_20261121.csv");
        csv("product_master_20261122.csv");
        csv("product_master_20261123.csv");
        csv("product_master_20261124.csv.tmp");

        ProductSyncInboxFile failed = history22(Trigger.SCHEDULE);
        failed.fail("필수 열이 없습니다", null);
        ProductSyncInboxFile done = history22(Trigger.MANUAL);
        done.done(staged(100), applied());
        when(historyRepository.findByFileNameInOrderByIdDesc(anyCollection())).thenReturn(List.of(done, failed));   // 최신 줄 먼저

        InboxListResponse r = service.list();

        assertTrue(r.dirExists());
        assertFalse(r.running());
        assertEquals(List.of("product_master_20261124.csv.tmp"), r.ignoredFiles());
        assertEquals(3, r.files().size());

        InboxListResponse.Item d23 = r.files().get(0);
        assertEquals("product_master_20261123.csv", d23.fileName());
        assertEquals(FileStatus.PENDING, d23.status());
        assertEquals(0, d23.attemptCount());
        assertNull(d23.result());

        InboxListResponse.Item d22 = r.files().get(1);
        assertEquals(FileStatus.DONE, d22.status());
        assertEquals(2, d22.attemptCount());
        assertEquals("MANUAL", d22.triggeredBy());
        assertEquals(3, d22.result().createdCount());

        assertEquals(FileStatus.SUPERSEDED, r.files().get(2).status());
    }

    @Test
    void 목록은_폴더가_없으면_빈_목록과_dirExists_false() {
        ProductSyncInboxService noDir = new ProductSyncInboxService(dir.resolve("없음").toString(),
                stagingLoader, applyService, loadService, historyRepository);

        InboxListResponse r = noDir.list();

        assertFalse(r.dirExists());
        assertTrue(r.files().isEmpty());
    }

    private static ProductSyncInboxFile history22(Trigger trigger) {
        return ProductSyncInboxFile.start("product_master_20261122.csv", LocalDate.of(2026, 11, 22), 10L,
                LocalDateTime.now(), trigger);
    }
}
