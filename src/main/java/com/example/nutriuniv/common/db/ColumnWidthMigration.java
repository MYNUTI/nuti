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
 * 부팅 시 문자열 컬럼 넓히기 — ddl-auto=update 는 이미 있는 컬럼의 길이를 늘리지 않는다. 여러 번 돌아도 같은 결과(멱등).
 * <ul>
 *   <li>report_no(품목보고번호): 원천에 「19980262020555; 19980448005336」처럼 여러 번호가 한 칸에 오는 행이 있다(11-25 파일 1,430행).
 *       hash 대상이라 자르지 않고 그대로 담는다 — 20 → 255</li>
 * </ul>
 * 지금 길이가 목표보다 짧을 때만 바꾼다. varchar 를 넓히는 것은 표를 다시 쓰지 않는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ColumnWidthMigration implements ApplicationRunner {

    record Widen(String table, String column, int length) {}

    static final List<Widen> COLUMNS = List.of(
            new Widen("products", "report_no", 255),
            new Widen("product_staging", "report_no", 255));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (Widen w : COLUMNS) {
            try {
                widen(w);
            } catch (Exception e) {
                log.error("[MIGRATION] {}.{} 길이 늘리기 실패", w.table(), w.column(), e);
            }
        }
    }

    private void widen(Widen w) {
        List<Integer> current = jdbcTemplate.queryForList("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Integer.class, w.table(), w.column());
        if (current.isEmpty() || current.get(0) == null || current.get(0) >= w.length()) return;
        jdbcTemplate.execute("ALTER TABLE " + w.table() + " ALTER COLUMN " + w.column() + " TYPE varchar(" + w.length() + ")");
        log.info("[MIGRATION] {}.{} 길이 {} → {}", w.table(), w.column(), current.get(0), w.length());
    }
}
