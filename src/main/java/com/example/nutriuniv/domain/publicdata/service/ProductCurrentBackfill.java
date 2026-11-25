package com.example.nutriuniv.domain.publicdata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 부팅 시 한 번 — 예전 최신화가 원천에서 사라졌다고 비활성(is_active=false)으로 꺼 둔 제품을
 * 켜고 현행 아님(is_current=false)으로 옮긴다 (DATA_000 · DATA_003 11-22: 검색 · 스캔 · 기록에는 남기고 랭킹 · 추천에서만 뺀다).
 * <ul>
 *   <li>대상: 꺼져 있고, 아직 현행이고(새 방식이 손대지 않음), 마지막 변경 이력이 SYNC_DEACTIVATE 인 제품.
 *       관리자가 직접 끈 제품은 마지막 이력이 다르거나 이미 현행 아님이라 건드리지 않는다</li>
 *   <li>옮긴 뒤에는 켜져 있어 다시 대상이 되지 않는다 — 다시 돌려도 같다</li>
 *   <li>실패해도 서버 기동은 막지 않는다. 다음 기동 때 다시 해 본다</li>
 * </ul>
 */
@Slf4j
@Component
public class ProductCurrentBackfill implements ApplicationRunner {

    static final String LEGACY = """
            SELECT p.id FROM products p
            WHERE  p.is_active = FALSE
              AND  p.is_current = TRUE
              AND  (SELECT l.source FROM product_change_logs l
                    WHERE l.product_id = p.id
                    ORDER BY l.created_at DESC, l.id DESC LIMIT 1) = 'SYNC_DEACTIVATE'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public ProductCurrentBackfill(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int moved = moveLegacy();
            if (moved > 0) log.info("[SYNC] 예전 최신화가 끈 제품 {}건을 켜고 현행 아님으로 옮겼습니다", moved);
        } catch (Exception e) {
            log.error("[SYNC] 예전 최신화가 끈 제품 옮기기 실패 — 다음 기동 때 다시 해 봅니다", e);
        }
    }

    /** 이력과 상태를 한 트랜잭션으로. 옮긴 수. */
    int moveLegacy() {
        Integer n = txTemplate.execute(status -> {
            jdbcTemplate.update("""
                INSERT INTO product_change_logs (product_id, source, summary, created_at)
                SELECT x.id, 'SYNC_DEACTIVATE', '적재 최신화 정책 변경: 비활성 → 현행 아님(검색 · 스캔에는 보임, 랭킹 · 추천에서만 빠짐)', now()
                FROM (""" + LEGACY + ") x");
            return jdbcTemplate.update("UPDATE products SET is_active = TRUE, is_current = FALSE, updated_at = now() WHERE id IN (" + LEGACY + ")");
        });
        return n == null ? 0 : n;
    }
}
