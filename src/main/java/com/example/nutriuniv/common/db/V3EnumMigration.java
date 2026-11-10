package com.example.nutriuniv.common.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 부팅 시 v3 상태 · 유형 값 옮기기 — 수동 SQL 없이 배포만으로 동작하게 한다. 여러 번 돌아도 같은 결과(멱등).
 * <ol>
 *   <li>Hibernate 가 표를 처음 만들 때 enum 컬럼에 붙인 CHECK 제약(옛 값 목록)을 지운다 — ddl-auto=update 는 이 제약을 고치지 않아 새 값이 막힌다</li>
 *   <li>분석 요청 상태 WAITING → ANALYZING, PROCESSING → REVIEWING, HOLD → FAILED</li>
 *   <li>제보 항목 NAME · IMAGE → NAME_OR_IMAGE, 제보 상태 PROCESSING → RECEIVED</li>
 * </ol>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class V3EnumMigration implements ApplicationRunner {

    /** [표, 컬럼] — enum 문자열 컬럼. */
    private static final List<String[]> ENUM_COLUMNS = List.of(
            new String[]{"analysis_requests", "status"},
            new String[]{"analysis_request_events", "channel"},
            new String[]{"product_reports", "report_type"},
            new String[]{"product_reports", "status"});

    private static final List<String> UPDATES = List.of(
            "UPDATE analysis_requests SET status = 'ANALYZING' WHERE status = 'WAITING'",
            "UPDATE analysis_requests SET status = 'REVIEWING' WHERE status = 'PROCESSING'",
            "UPDATE analysis_requests SET status = 'FAILED' WHERE status = 'HOLD'",
            "UPDATE product_reports SET report_type = 'NAME_OR_IMAGE' WHERE report_type IN ('NAME', 'IMAGE')",
            "UPDATE product_reports SET status = 'RECEIVED' WHERE status = 'PROCESSING'");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String[] tc : ENUM_COLUMNS) dropEnumChecks(tc[0], tc[1]);
            int moved = 0;
            for (String sql : UPDATES) moved += jdbcTemplate.update(sql);
            if (moved > 0) log.info("[MIGRATION] v3 상태 · 유형 값 {}행 옮김", moved);
        } catch (Exception e) {
            log.error("[MIGRATION] v3 상태 · 유형 값 옮기기 실패", e);
        }
    }

    private void dropEnumChecks(String table, String column) {
        if (!Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table))) return;
        List<String> names = jdbcTemplate.queryForList("""
                SELECT c.conname
                  FROM pg_constraint c
                  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                 WHERE c.contype = 'c' AND c.conrelid = to_regclass(?) AND a.attname = ?
                """, String.class, table, column);
        for (String name : names) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
            log.info("[MIGRATION] {}.{} CHECK 제약 {} 삭제", table, column, name);
        }
    }
}
