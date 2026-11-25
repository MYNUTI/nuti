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
import java.util.Map;

/**
 * 부팅 시 컬럼 넓히기 — ddl-auto=update 는 이미 있는 컬럼의 길이 · 자릿수를 늘리지 않는다. 여러 번 돌아도 같은 결과(멱등).
 * <ul>
 *   <li>report_no(품목보고번호): 원천에 「19980262020555; 19980448005336」처럼 여러 번호가 한 칸에 오는 행이 있다(11-25 파일 1,430행).
 *       hash 대상이라 자르지 않고 그대로 담는다 — varchar 20 → 255</li>
 *   <li>1회 제공량 영양값: 원천에 10만이 넘는 값이 있다(11-25 파일 3,820행, 최대 1,200만 — 나트륨 · 열량이 대부분).
 *       numeric(8,3)(최대 99,999.999)에 안 들어가 제품 전체가 실패했다 — numeric(12,3). 100g당 값은 넘는 행이 없어 그대로</li>
 * </ul>
 * 지금 길이 · 자릿수가 목표보다 작을 때만 바꾼다. 소수 자릿수(scale)는 그대로 두고 넓히기만 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ColumnWidthMigration implements ApplicationRunner {

    /** type: "varchar" 면 size = 길이, "numeric" 이면 size = 전체 자릿수(소수 3자리 고정). */
    record Widen(String table, String column, String type, int size) {
        String ddlType() {
            return "numeric".equals(type) ? "numeric(" + size + ",3)" : "varchar(" + size + ")";
        }
    }

    private static final List<String> SERVING_NUTRIENTS = List.of(
            "calories", "carbohydrate", "sugar", "protein", "fat", "saturated_fat", "trans_fat",
            "cholesterol", "sodium", "dietary_fiber");

    static final List<Widen> COLUMNS;
    static {
        List<Widen> cols = new java.util.ArrayList<>(List.of(
                new Widen("products", "report_no", "varchar", 255),
                new Widen("product_staging", "report_no", "varchar", 255)));
        for (String c : SERVING_NUTRIENTS) cols.add(new Widen("product_nutrients", c, "numeric", 12));
        COLUMNS = List.copyOf(cols);
    }

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (Widen w : COLUMNS) {
            try {
                widen(w);
            } catch (Exception e) {
                log.error("[MIGRATION] {}.{} 넓히기 실패", w.table(), w.column(), e);
            }
        }
    }

    private void widen(Widen w) {
        List<Map<String, Object>> found = jdbcTemplate.queryForList("""
                SELECT character_maximum_length AS len, numeric_precision AS prec
                  FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, w.table(), w.column());
        if (found.isEmpty()) return;   // 표가 아직 없으면 Hibernate 가 새 크기로 만든다
        Object current = found.get(0).get("numeric".equals(w.type()) ? "prec" : "len");
        if (!(current instanceof Number n) || n.intValue() >= w.size()) return;
        jdbcTemplate.execute("ALTER TABLE " + w.table() + " ALTER COLUMN " + w.column() + " TYPE " + w.ddlType());
        log.info("[MIGRATION] {}.{} {} → {}", w.table(), w.column(), n.intValue(), w.ddlType());
    }
}
