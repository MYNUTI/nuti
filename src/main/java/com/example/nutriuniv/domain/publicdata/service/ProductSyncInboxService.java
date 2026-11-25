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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * CSV 수신함 — 파이프라인이 /data/csv 에 둔 34컬럼 CSV 를 제품 데이터에 반영한다.
 * <ul>
 *   <li>파일 이름은 product_master_YYYYMMDD.csv 만 읽는다. 쓰는 중인 .tmp 등은 무시한다</li>
 *   <li>파일마다 전체 제품 데이터라 <b>가장 새 날짜 파일 하나만</b> 반영한다. 그보다 오래된 미처리 파일은 건너뛴다(SUPERSEDED)</li>
 *   <li>처리 = 복제 테이블 채우기(ProductStagingLoader) → 반영(ProductSyncApplyService). 파일에 없는 제품은 원천에서 사라진 것으로 보고
 *       현행 아님으로 돌린다 — 검색 · 스캔 · 기록에는 남고 랭킹 · 추천에서만 빠진다(출처별 10% 안전장치는 반영 서비스 그대로)</li>
 *   <li>처리 이력(product_sync_inbox_files)에 DONE 이 있는 파일은 자동 실행이 다시 처리하지 않는다. 실패한 파일은 다음 자동 실행에서 다시 해 본다</li>
 *   <li>한 번에 한 파일만, 뒤에서(별도 스레드) 처리한다 — 30만 행 반영은 HTTP 요청 · 스케줄러 스레드를 오래 잡는다</li>
 * </ul>
 * 폴더는 읽기만 한다(파이프라인 볼륨이 읽기 전용으로 붙는다). 서버 한 대 전제.
 */
@Slf4j
@Service
public class ProductSyncInboxService {

    static final Pattern FILE_NAME = Pattern.compile("^product_master_(\\d{8})\\.csv$");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 폴더에 있는 이름 규칙에 맞는 CSV 한 개. */
    record InboxCsv(String fileName, LocalDate fileDate, Path path, long size, LocalDateTime modifiedAt) {}

    private final Path dir;
    private final ProductStagingLoader stagingLoader;
    private final ProductSyncApplyService applyService;
    private final ProductLoadService loadService;
    private final ProductSyncInboxFileRepository historyRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProductSyncInboxService(@Value("${product-sync.inbox.dir:/data/csv}") String dir,
                                   ProductStagingLoader stagingLoader, ProductSyncApplyService applyService,
                                   ProductLoadService loadService, ProductSyncInboxFileRepository historyRepository) {
        this.dir = Path.of(dir);
        this.stagingLoader = stagingLoader;
        this.applyService = applyService;
        this.loadService = loadService;
        this.historyRepository = historyRepository;
    }

    /** 서버가 처리 도중 꺼졌으면(배포 등) PROCESSING 이 남는다 — 실패로 돌려 다음 자동 실행이 다시 하게 한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterrupted() {
        int n = historyRepository.failInterrupted(LocalDateTime.now(), "서버가 처리 도중 다시 시작되어 중단됨");
        if (n > 0) log.warn("[INBOX] 처리 도중 중단된 이력 {}건을 실패로 돌림 — 다음 자동 실행에서 다시 처리합니다", n);
    }

    // ── 자동 실행 ────────────────────────────────────────────────────────────

    /** 주 1회 정해진 요일·시각에(ProductSyncInboxScheduler) — 가장 새 파일이 아직 반영 전이면 처리를 시작한다. 없으면 건너뛴다. */
    public void runScheduled() {
        if (!Files.isDirectory(dir)) {
            log.warn("[INBOX] 수신함 폴더가 없습니다 — {} (볼륨이 붙어 있는지 확인)", dir);
            return;
        }
        Optional<InboxCsv> newest = newest(scan());
        if (newest.isEmpty()) {
            log.info("[INBOX] 새 CSV 없음 — 건너뜀");
            return;
        }
        InboxCsv target = newest.get();
        if (historyRepository.existsByFileNameAndStatus(target.fileName(), Status.DONE)) {
            log.info("[INBOX] 최신 파일 {} 은 이미 반영함 — 건너뜀", target.fileName());
            return;
        }
        if (loadService.isLoading()) {
            log.warn("[INBOX] 다른 제품 적재·반영이 진행 중이라 {} 처리를 건너뜀 — 다음 자동 실행 또는 POST /admin/product-sync/inbox/run", target.fileName());
            return;
        }
        if (!start(target, Trigger.SCHEDULE).isPresent()) {
            log.info("[INBOX] 다른 파일을 처리 중이라 건너뜀");
        }
    }

    // ── 수동 실행 ────────────────────────────────────────────────────────────

    /**
     * @param fileName null 이면 가장 새 파일. 지정하면 그 파일(product_master_YYYYMMDD.csv)
     * @param force    이미 반영한 파일도 다시 처리한다
     */
    public InboxRunResponse runManual(String fileName, boolean force) {
        if (!Files.isDirectory(dir)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "수신함 폴더가 없습니다: " + dir);
        }
        InboxCsv target;
        if (fileName == null || fileName.isBlank()) {
            target = newest(scan()).orElseThrow(() ->
                    new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "수신함에 product_master_YYYYMMDD.csv 파일이 없습니다."));
        } else {
            String name = fileName.trim();
            if (!FILE_NAME.matcher(name).matches()) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "파일 이름은 product_master_YYYYMMDD.csv 형식이어야 합니다.");
            }
            target = scan().stream().filter(f -> f.fileName().equals(name)).findFirst().orElseThrow(() ->
                    new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "수신함에 없는 파일입니다: " + name));
        }

        if (!force && historyRepository.existsByFileNameAndStatus(target.fileName(), Status.DONE)) {
            throw new CustomException(ErrorCode.STATE_CONFLICT,
                    target.fileName() + " 은 이미 반영했습니다. 다시 처리하려면 force=true 로 부르세요.");
        }
        if (loadService.isLoading()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "다른 제품 적재·반영이 진행 중입니다. 끝난 뒤 다시 실행하세요.");
        }
        return start(target, Trigger.MANUAL).orElseThrow(() ->
                new CustomException(ErrorCode.STATE_CONFLICT, "다른 CSV 를 처리하는 중입니다. 끝난 뒤 다시 실행하세요."));
    }

    // ── 처리 ─────────────────────────────────────────────────────────────────

    /**
     * 처리 중이 아니면 이력을 만들고 뒤에서 처리를 시작한다. 이미 처리 중이면 빈 값.
     * 돌려주는 값은 스레드를 띄우기 전에 만든다 — 이력 객체는 처리 스레드가 바로 고치므로 응답에 쓰면 DONE 이 섞일 수 있다.
     */
    private Optional<InboxRunResponse> start(InboxCsv target, Trigger trigger) {
        if (!running.compareAndSet(false, true)) return Optional.empty();
        ProductSyncInboxFile history;
        try {
            history = historyRepository.save(ProductSyncInboxFile.start(
                    target.fileName(), target.fileDate(), target.size(), target.modifiedAt(), trigger));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        log.info("[INBOX] {} 처리 시작 ({}) — {} bytes", target.fileName(), trigger, target.size());
        InboxRunResponse response = new InboxRunResponse(history.getId(), history.getFileName(), Status.PROCESSING.name());

        Thread t = new Thread(() -> {
            try {
                process(history, target.path());
            } finally {
                running.set(false);
            }
        }, "product-sync-inbox");
        t.setDaemon(true);
        t.start();
        return Optional.of(response);
    }

    /** 복제 테이블 채우기 → 반영. 결과를 이력에 남긴다. 테스트에서 동기로 부를 수 있게 열어 둠. */
    void process(ProductSyncInboxFile history, Path path) {
        StagingLoadResponse staged = null;
        try {
            staged = stagingLoader.load(path);
            if (staged.stagedCount() == 0) {
                throw new IllegalStateException("넣을 행이 없습니다(헤더만 있거나 master_id 가 전부 비어 있음)");
            }
            SyncApplyResponse applied = applyService.apply(false);
            history.done(staged, applied);
            historyRepository.save(history);
            log.info("[INBOX] {} 반영 완료 — 행 {} · 새로 {} · 수정 {} · 그대로 {} · 보호 {} · 다시 현행 {} · 현행 아님 {}/{}{} · 실패 {} · hash 다름 {}",
                    history.getFileName(), staged.stagedCount(), applied.createdCount(), applied.updatedCount(),
                    applied.unchangedCount(), applied.protectedCount(), applied.reactivatedCount(),
                    applied.deactivatedCount(), applied.disappearedCount(),
                    applied.deactivationSkipped() ? " (10% 넘는 출처는 현행 아님 건너뜀)" : "",
                    applied.failCount(), staged.hashMismatchCount());
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("[INBOX] {} 처리 실패 — {}", history.getFileName(), reason, e);
            history.fail(reason, staged);
            try {
                historyRepository.save(history);
            } catch (RuntimeException saveError) {
                log.error("[INBOX] {} 실패 이력 저장 실패", history.getFileName(), saveError);
            }
        }
    }

    // ── 목록 ─────────────────────────────────────────────────────────────────

    public InboxListResponse list() {
        if (!Files.isDirectory(dir)) {
            return new InboxListResponse(dir.toString(), false, running.get(), List.of(), List.of());
        }
        List<String> ignored = new ArrayList<>();
        List<InboxCsv> files = scan(ignored);

        Map<String, List<ProductSyncInboxFile>> byName = new HashMap<>();
        if (!files.isEmpty()) {
            for (ProductSyncInboxFile h : historyRepository.findByFileNameInOrderByIdDesc(
                    files.stream().map(InboxCsv::fileName).toList())) {
                byName.computeIfAbsent(h.getFileName(), k -> new ArrayList<>()).add(h);   // 최신 줄이 먼저
            }
        }
        String newestName = newest(files).map(InboxCsv::fileName).orElse(null);

        List<InboxListResponse.Item> items = new ArrayList<>();
        for (InboxCsv f : files) {
            List<ProductSyncInboxFile> attempts = byName.getOrDefault(f.fileName(), List.of());
            ProductSyncInboxFile last = attempts.isEmpty() ? null : attempts.get(0);
            items.add(item(f, last, attempts.size(), f.fileName().equals(newestName)));
        }
        return new InboxListResponse(dir.toString(), true, running.get(), items, ignored);
    }

    static InboxListResponse.Item item(InboxCsv f, ProductSyncInboxFile last, int attempts, boolean newest) {
        if (last == null) {
            return new InboxListResponse.Item(f.fileName(), f.fileDate(), f.size(), f.modifiedAt(),
                    newest ? FileStatus.PENDING : FileStatus.SUPERSEDED, 0, null, null, null, null, null);
        }
        InboxListResponse.Result result = new InboxListResponse.Result(
                last.getStagedCount(), last.getHashMismatchCount(), last.getCreatedCount(), last.getUpdatedCount(),
                last.getUnchangedCount(), last.getProtectedCount(), last.getReactivatedCount(),
                last.getDeactivatedCount(), last.getDisappearedCount(), last.getDeactivationSkipped(), last.getFailCount());
        return new InboxListResponse.Item(f.fileName(), f.fileDate(), f.size(), f.modifiedAt(),
                FileStatus.valueOf(last.getStatus().name()), attempts, last.getTriggeredBy().name(),
                last.getStartedAt(), last.getFinishedAt(), last.getFailReason(), result);
    }

    // ── 폴더 읽기 ────────────────────────────────────────────────────────────

    List<InboxCsv> scan() {
        return scan(null);
    }

    /** 폴더의 product_master_YYYYMMDD.csv, 날짜 최신 먼저. 규칙에 안 맞는 파일 이름은 ignored 에 담는다. */
    List<InboxCsv> scan(List<String> ignored) {
        List<InboxCsv> out = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                Optional<LocalDate> date = parseDate(name);
                if (date.isEmpty()) {
                    if (ignored != null) ignored.add(name);
                    continue;
                }
                long size = Files.size(p);
                LocalDateTime modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZONE);
                out.add(new InboxCsv(name, date.get(), p, size, modified));
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "수신함 폴더를 읽을 수 없습니다: " + e.getMessage());
        }
        out.sort(Comparator.comparing(InboxCsv::fileDate).reversed());
        if (ignored != null) Collections.sort(ignored);
        return out;
    }

    static Optional<InboxCsv> newest(List<InboxCsv> files) {
        return files.stream().max(Comparator.comparing(InboxCsv::fileDate));
    }

    /** product_master_YYYYMMDD.csv 의 날짜. 규칙에 안 맞거나 없는 날짜면 빈 값. */
    static Optional<LocalDate> parseDate(String fileName) {
        Matcher m = FILE_NAME.matcher(fileName);
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(m.group(1), YMD.withResolverStyle(java.time.format.ResolverStyle.STRICT)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}