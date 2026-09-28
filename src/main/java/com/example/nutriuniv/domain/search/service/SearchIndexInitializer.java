package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.common.util.SearchNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 부팅 시 검색 준비 — 수동 SQL 없이 배포만으로 동작하게 한다.
 * <ol>
 *   <li>search_settings 기본값·동의어 사전 예시 시드(비어 있을 때만)</li>
 *   <li>products.name_normalized/name_chosung 가 비어 있는 행 백필 (컬럼 신설 직후). 이후는 엔티티 콜백이 저장 시마다 채운다</li>
 * </ol>
 * 백필은 초성 변환이 필요해 Java 에서 계산하고 2,000행씩 배치 UPDATE 한다(WHERE id). 기동을 막지 않도록 별도 스레드.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer implements ApplicationRunner {

    private static final int BATCH = 2_000;

    private final SearchSettingService settingService;
    private final SearchDictionaryService dictionaryService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            settingService.ensureDefaults();
            dictionaryService.ensureStarterSeed();
        } catch (Exception e) {
            log.error("[SEARCH] 설정·사전 시드 실패 — 코드 기본값으로 동작", e);
        }
        try {
            Long missing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products WHERE name_normalized IS NULL", Long.class);
            if (missing != null && missing > 0) {
                log.info("[SEARCH] 검색 색인 백필 시작 — {}행", missing);
                Thread t = new Thread(this::backfill, "search-index-backfill");
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            log.error("[SEARCH] 색인 백필 판단 실패", e);
        }
    }

    void backfill() {
        long total = 0;
        try {
            while (true) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, name FROM products WHERE name_normalized IS NULL ORDER BY id LIMIT " + BATCH);
                if (rows.isEmpty()) break;
                List<Object[]> params = new ArrayList<>(rows.size());
                for (Map<String, Object> r : rows) {
                    String name = (String) r.get("name");
                    params.add(new Object[]{
                            SearchNormalizer.normalize(name),
                            SearchNormalizer.chosung(name),
                            ((Number) r.get("id")).longValue()
                    });
                }
                jdbcTemplate.batchUpdate("UPDATE products SET name_normalized = ?, name_chosung = ? WHERE id = ?", params);
                total += rows.size();
                if (rows.size() < BATCH) break;
            }
            log.info("[SEARCH] 검색 색인 백필 완료 — {}행", total);
        } catch (Exception e) {
            log.error("[SEARCH] 검색 색인 백필 실패 ({}행 처리 후) — 다음 부팅에서 이어서 처리", total, e);
        }
    }
}
