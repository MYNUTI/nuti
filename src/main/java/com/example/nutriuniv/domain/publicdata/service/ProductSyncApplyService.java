package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.grade.service.GradeBatchService;
import com.example.nutriuniv.domain.product.service.ProductCsvRowService;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
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
 *   <li>최신화가 비활성으로 돌렸던 제품이 원천에 다시 나타나면 살린다(관리자가 직접 비활성으로 돌린 제품은 그대로)</li>
 *   <li>원천에서 사라진 제품은 지우지 않고 is_active=false 로 돌리고 변경 이력(SYNC_DEACTIVATE)을 남긴다.
 *       단, 사라진 제품이 적재 제품의 10%를 넘으면 수집이 덜 된 것으로 보고 비활성을 건너뛴다(allowMassDeactivation 으로 강제)</li>
 *   <li>바뀐 것이 있으면 등급을 백그라운드로 전량 재계산한다</li>
 * </ol>
 * 적재 기록(ProductLoadService)을 같이 쓴다 — 반영 중에는 CSV 적재·다른 반영·등급 기준 재산출이 막힌다.
 */
@Slf4j
@Service
public class ProductSyncApplyService {

    /** 사라진 제품이 적재 제품의 이 비율을 넘으면 비활성을 건너뛴다. */
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
        long disappeared = stagingRepository.findDisappearedProductIds().size();
        long activeLoaded = stagingRepository.countActiveLoadedProducts();
        return new SyncPreviewResponse(staged, create, update, reactivate, disappeared, activeLoaded,
                exceedsMassDeactivation(disappeared, activeLoaded));
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
        List<Long> disappeared;
        boolean skipDeactivation;
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

            disappeared = stagingRepository.findDisappearedProductIds();
            long activeLoaded = stagingRepository.countActiveLoadedProducts();
            skipDeactivation = !allowMassDeactivation && exceedsMassDeactivation(disappeared.size(), activeLoaded);
            if (skipDeactivation) {
                log.warn("[SYNC] 원천에서 사라진 제품 {}건이 적재 제품 {}건의 {}%를 넘어 비활성을 건너뜁니다 — 수집이 덜 됐는지 확인하세요",
                        disappeared.size(), activeLoaded, (int) (MAX_DEACTIVATION_RATIO * 100));
            } else {
                c.deactivated = deactivate(disappeared);
            }
        } catch (RuntimeException e) {
            loadService.fail(loadId);
            throw e;
        }

        int processed = c.created + c.updated + c.protectedRows + c.failed;
        int unchanged = (int) staged - processed;
        loadService.finish(loadId, (int) staged, c.created, c.updated, unchanged, c.protectedRows, c.failed);
        log.info("[SYNC] 반영 id={} — 복제 {} · 후보 {} · 새로 {} · 수정 {} · 보호 {} · 살림 {} · 비활성 {}/{}{} · 실패 {}",
                loadId, staged, changed.size(), c.created, c.updated, c.protectedRows, c.reactivated,
                c.deactivated, disappeared.size(), skipDeactivation ? "(건너뜀)" : "", c.failed);

        if (c.created + c.updated + c.reactivated + c.deactivated > 0) {
            gradeBatchService.recomputeAsync(LOAD_NAME);
        }

        return new SyncApplyResponse(loadId, staged, changed.size(), c.created, c.updated, unchanged, c.protectedRows,
                c.reactivated, c.deactivated, disappeared.size(), skipDeactivation, c.failed, fails);
    }

    private void applyOne(ChangedRow ch, Map<String, String> row, Map<String, Category> categoriesByCode,
                          Map<String, Brand> brandCache, Counts c, List<SyncApplyResponse.FailItem> fails) {
        try {
            if (row == null) throw new IllegalStateException("복제 테이블에서 행을 찾을 수 없습니다");
            String barcode = row.get("barcode");
            ProductCsvRowService.LoadItem item = new ProductCsvRowService.LoadItem(
                    0, ch.masterId(), row, new ArrayList<>(List.of(barcode == null ? "" : barcode)));
            switch (rowService.apply(item, categoriesByCode, brandCache, false)) {
                case CREATED -> c.created++;
                case UPDATED -> c.updated++;
                case PROTECTED -> c.protectedRows++;
                case UNCHANGED -> { }   // 다시 나타났지만 값은 그대로인 경우
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
        int created, updated, protectedRows, reactivated, deactivated, failed;
    }
}