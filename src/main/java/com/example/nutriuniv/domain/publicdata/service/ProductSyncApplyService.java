package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.grade.service.GradeBatchService;
import com.example.nutriuniv.domain.product.service.ProductCsvRowService;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.publicdata.dto.SourceDeactivation;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncPreviewResponse;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository.ChangedRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * 복제 테이블 → 실제 product 반영 (공공데이터 최신화 4단계).
 * <ol>
 *   <li>hash 가 다르거나 새로 생긴 행만 골라 ProductCsvRowService.apply 에 넘긴다 — master_id 조회 · 관리자가 고친 제품 보호 ·
 *       바코드 정규화 · 브랜드 · 분류 · 성분 · 분석 상태를 CSV 적재와 똑같이 처리하고, 제품마다 따로 커밋한다</li>
 *   <li>파일에 같이 보이는 칩 열(extra_categories)이 있으면 칩도 반영한다 — hash 는 같고 칩만 바뀐 제품은 칩만 바꾸고 수정 건수에 넣는다.
 *       열이 없는 파일이면 기존 칩을 그대로 둔다</li>
 *   <li>최신화가 비활성으로 돌렸던 제품이 원천에 다시 나타나면 살린다(관리자가 직접 비활성으로 돌린 제품은 그대로)</li>
 *   <li>원천에서 사라진 제품은 지우지 않고 is_active=false 로 돌리고 변경 이력(SYNC_DEACTIVATE)을 남긴다.
 *       <b>출처별로 관리한다</b> — 대기 테이블에 들어 있는 출처(source)의 제품만 대상이고, 다른 경로로 들어온 출처(올리브영 CSV 등)는 건드리지 않는다.
 *       출처마다 사라진 제품이 그 출처 적재 제품의 10%를 넘으면 수집이 덜 된 것으로 보고 그 출처만 비활성을 건너뛴다(allowMassDeactivation 으로 강제)</li>
 *   <li>바뀐 것이 있으면 등급을 백그라운드로 전량 재계산한다</li>
 * </ol>
 * 적재 기록(ProductLoadService)을 같이 쓴다 — 반영 중에는 CSV 적재·다른 반영·등급 기준 재산출이 막힌다.
 */
@Slf4j
@Service
public class ProductSyncApplyService {

    /** 한 출처에서 사라진 제품이 그 출처 적재 제품의 이 비율을 넘으면 그 출처는 비활성을 건너뛴다. */
    static final double MAX_DEACTIVATION_RATIO = 0.10;
    static final String LOAD_NAME = "product-sync";
    private static final int CHUNK = 500;
    private static final int FAIL_LIMIT = 100;

    private final ProductStagingRepository stagingRepository;
    private final ProductCsvRowService rowService;
    private final ProductLoadService loadService;
    private final CategoryRepository categoryRepository;
    private final GradeBatchService gradeBatchService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public ProductSyncApplyService(ProductStagingRepository stagingRepository, ProductCsvRowService rowService,
                                   ProductLoadService loadService, CategoryRepository categoryRepository,
                                   GradeBatchService gradeBatchService, JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager transactionManager) {
        this.stagingRepository = stagingRepository;
        this.rowService = rowService;
        this.loadService = loadService;
        this.categoryRepository = categoryRepository;
        this.gradeBatchService = gradeBatchService;
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 반영하면 무엇이 바뀌는지만 센다. */
    public SyncPreviewResponse preview() {
        long staged = stagingRepository.count();
        List<ChangedRow> changed = stagingRepository.findChanged();
        long create = changed.stream().filter(r -> r.productId() == null).count();
        long reactivate = changed.stream().filter(ChangedRow::reactivate).count();
        long update = changed.size() - create - reactivate;

        Map<String, List<Long>> disappeared = groupBySource(stagingRepository.findDisappeared());
        Map<String, Long> activeLoaded = stagingRepository.countActiveLoadedBySource();
        List<SourceDeactivation> sources = new ArrayList<>();
        for (String source : sourcesOf(disappeared, activeLoaded)) {
            int gone = disappeared.getOrDefault(source, List.of()).size();
            long active = activeLoaded.getOrDefault(source, 0L);
            sources.add(new SourceDeactivation(source, active, gone, 0, exceedsMassDeactivation(gone, active)));
        }
        long disappearedTotal = sources.stream().mapToLong(SourceDeactivation::disappearedCount).sum();
        long activeTotal = activeLoaded.values().stream().mapToLong(Long::longValue).sum();
        boolean mass = sources.stream().anyMatch(SourceDeactivation::skipped);
        return new SyncPreviewResponse(staged, create, update, reactivate, disappearedTotal, activeTotal, mass, sources);
    }

    public SyncApplyResponse apply(boolean allowMassDeactivation) {
        long staged = stagingRepository.count();
        if (staged == 0) {
            // 빈 복제 테이블로 반영하면 적재 제품 전부가 「사라짐」이 된다
            throw new CustomException(ErrorCode.STATE_CONFLICT, "복제 테이블이 비어 있습니다. 먼저 채운 뒤 반영하세요.");
        }

        Long loadId = loadService.start(LOAD_NAME);
        Counts c = new Counts();
        List<SyncApplyResponse.FailItem> fails = new ArrayList<>();
        List<ChangedRow> changed;
        List<SourceDeactivation> sources = new ArrayList<>();
        try {
            changed = stagingRepository.findChanged();
            Map<String, Category> categoriesByCode = new HashMap<>();
            for (Category cat : categoryRepository.findAllByCodeIsNotNull()) categoriesByCode.put(cat.getCode(), cat);
            Map<String, Brand> brandCache = new HashMap<>();

            for (int from = 0; from < changed.size(); from += CHUNK) {
                List<ChangedRow> chunk = changed.subList(from, Math.min(from + CHUNK, changed.size()));
                Map<String, Map<String, String>> rows = stagingRepository.findRows(chunk.stream().map(ChangedRow::masterId).toList());
                for (ChangedRow ch : chunk) {
                    applyOne(ch, rows.get(ch.masterId()), categoriesByCode, brandCache, c, fails);
                }
            }

            Map<String, List<Long>> disappeared = groupBySource(stagingRepository.findDisappeared());
            Map<String, Long> activeLoaded = stagingRepository.countActiveLoadedBySource();
            for (String source : sourcesOf(disappeared, activeLoaded)) {
                List<Long> ids = disappeared.getOrDefault(source, List.of());
                long active = activeLoaded.getOrDefault(source, 0L);
                boolean skip = !allowMassDeactivation && exceedsMassDeactivation(ids.size(), active);
                int done = 0;
                if (skip) {
                    log.warn("[SYNC] 출처 {} — 사라진 제품 {}건이 적재 제품 {}건의 {}%를 넘어 비활성을 건너뜁니다. 수집이 덜 됐는지 확인하세요",
                            source, ids.size(), active, (int) (MAX_DEACTIVATION_RATIO * 100));
                } else {
                    done = deactivate(ids);
                }
                c.deactivated += done;
                sources.add(new SourceDeactivation(source, active, ids.size(), done, skip));
            }
        } catch (RuntimeException e) {
            loadService.fail(loadId);
            throw e;
        }

        int processed = c.created + c.updated + c.protectedRows + c.failed;
        int unchanged = (int) staged - processed;
        int disappearedTotal = sources.stream().mapToInt(SourceDeactivation::disappearedCount).sum();
        boolean anySkipped = sources.stream().anyMatch(SourceDeactivation::skipped);
        loadService.finish(loadId, (int) staged, c.created, c.updated, unchanged, c.protectedRows, c.failed);
        log.info("[SYNC] 반영 id={} — 대기 {} · 후보 {} · 새로 {} · 수정 {}(칩만 {}) · 보호 {} · 살림 {} · 비활성 {}/{} · 실패 {} · 출처별 {}",
                loadId, staged, changed.size(), c.created, c.updated, c.chipsOnly, c.protectedRows, c.reactivated,
                c.deactivated, disappearedTotal, c.failed, sources);

        if (c.created + c.updated + c.reactivated + c.deactivated > 0) {
            gradeBatchService.recomputeAsync(LOAD_NAME);
        }

        return new SyncApplyResponse(loadId, staged, changed.size(), c.created, c.updated, unchanged, c.protectedRows,
                c.reactivated, c.deactivated, disappearedTotal, anySkipped, sources, c.failed, fails);
    }

    /** 사라진 제품을 출처별로. 출처 순서는 이름순(응답 · 로그가 매번 같은 순서로 나오게). */
    private static Map<String, List<Long>> groupBySource(List<ProductStagingRepository.DisappearedRow> rows) {
        Map<String, List<Long>> out = new TreeMap<>();
        for (ProductStagingRepository.DisappearedRow r : rows) {
            out.computeIfAbsent(r.source(), k -> new ArrayList<>()).add(r.productId());
        }
        return out;
    }

    /** 대기 테이블이 맡는 출처 — 활성 적재 제품이 있거나 사라진 제품이 있는 출처, 이름순. */
    private static List<String> sourcesOf(Map<String, List<Long>> disappeared, Map<String, Long> activeLoaded) {
        TreeSet<String> all = new TreeSet<>(activeLoaded.keySet());
        all.addAll(disappeared.keySet());
        return List.copyOf(all);
    }

    private void applyOne(ChangedRow ch, Map<String, String> row, Map<String, Category> categoriesByCode,
                          Map<String, Brand> brandCache, Counts c, List<SyncApplyResponse.FailItem> fails) {
        try {
            if (row == null) throw new IllegalStateException("복제 테이블에서 행을 찾을 수 없습니다");
            String barcode = row.get("barcode");
            ProductCsvRowService.LoadItem item = new ProductCsvRowService.LoadItem(
                    0, ch.masterId(), row, new ArrayList<>(List.of(barcode == null ? "" : barcode)));
            String chips = row.get(ProductStagingRepository.EXTRA_CATEGORIES);   // null = 파일에 칩 열 없음 → 기존 칩 그대로
            boolean withChips = chips != null;
            switch (rowService.apply(item, categoriesByCode, brandCache, withChips)) {
                case CREATED -> c.created++;
                case UPDATED -> c.updated++;
                case PROTECTED -> c.protectedRows++;
                case UNCHANGED -> {   // hash 가 같음 — 다시 나타났거나 칩만 바뀐 경우
                    if (withChips && rowService.applyChips(ch.masterId(), chips, categoriesByCode)) {
                        c.updated++;
                        c.chipsOnly++;
                    }
                }
            }
            if (ch.reactivate() && reactivate(ch.productId())) c.reactivated++;
        } catch (Exception e) {
            brandCache.clear();   // 실패한 트랜잭션에서 만든 브랜드가 캐시에 남지 않게
            c.failed++;
            log.warn("[SYNC] master_id={} error={}", ch.masterId(), e.getMessage());
            if (fails.size() < FAIL_LIMIT) fails.add(new SyncApplyResponse.FailItem(ch.masterId(), e.getMessage()));
        }
    }

    private boolean reactivate(Long productId) {
        Integer n = txTemplate.execute(status -> {
            int updated = jdbcTemplate.update(
                    "UPDATE products SET is_active = TRUE, updated_at = now() WHERE id = ? AND is_active = FALSE", productId);
            if (updated > 0) {
                jdbcTemplate.update("""
                        INSERT INTO product_change_logs (product_id, source, summary, created_at)
                        VALUES (?, 'SYNC_REACTIVATE', '공공데이터 최신화: 원천에 다시 나타나 활성으로 되돌림', now())
                        """, productId);
            }
            return updated;
        });
        return n != null && n > 0;
    }

    /** 사라진 제품을 비활성으로 — CHUNK 단위로 커밋. 비활성으로 돌린 수. */
    private int deactivate(List<Long> productIds) {
        int total = 0;
        for (int from = 0; from < productIds.size(); from += CHUNK) {
            List<Long> chunk = productIds.subList(from, Math.min(from + CHUNK, productIds.size()));
            Integer n = txTemplate.execute(status -> {
                int done = 0;
                for (Long id : chunk) {
                    int updated = jdbcTemplate.update(
                            "UPDATE products SET is_active = FALSE, updated_at = now() WHERE id = ? AND is_active = TRUE", id);
                    if (updated > 0) {
                        jdbcTemplate.update("""
                                INSERT INTO product_change_logs (product_id, source, summary, created_at)
                                VALUES (?, 'SYNC_DEACTIVATE', '공공데이터 최신화: 원천에서 사라져 비활성으로 돌림', now())
                                """, id);
                        done++;
                    }
                }
                return done;
            });
            total += n == null ? 0 : n;
        }
        return total;
    }

    /** 사라진 제품이 기준 비율을 넘는가. 적재 제품이 없으면 넘지 않는다. */
    static boolean exceedsMassDeactivation(long disappeared, long activeLoaded) {
        if (activeLoaded == 0 || disappeared == 0) return false;
        return (double) disappeared / activeLoaded > MAX_DEACTIVATION_RATIO;
    }

    private static final class Counts {
        int created, updated, chipsOnly, protectedRows, reactivated, deactivated, failed;
    }
}