package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.product.util.ContentHashCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 복제 테이블(product_staging) 읽기·쓰기 — JDBC. 컬럼 이름이 적재 CSV 헤더와 같아서 행을 그대로 맵으로 주고받는다.
 * 30만 행 단위라 엔티티 saveAll 대신 배치 INSERT 를 쓴다(GradeBatchService 와 같은 이유).
 */
@Repository
@RequiredArgsConstructor
public class ProductStagingRepository {

    /** 같이 보이는 칩 열 — 적재 CSV 의 extra_categories 와 같은 이름. hash 대상이 아니다. */
    public static final String EXTRA_CATEGORIES = "extra_categories";

    /** product_staging 의 값 컬럼 — master_id + hash 대상 30칸 + content_hash + extra_categories. */
    public static final List<String> VALUE_COLUMNS;
    static {
        List<String> cols = new ArrayList<>();
        cols.add("master_id");
        cols.addAll(ContentHashCalculator.HASH_COLUMNS);
        cols.add("content_hash");
        cols.add(EXTRA_CATEGORIES);
        VALUE_COLUMNS = List.copyOf(cols);
    }

    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT_SQL = "INSERT INTO product_staging (" + String.join(", ", VALUE_COLUMNS) + ", computed_at) VALUES ("
            + String.join(", ", Collections.nCopies(VALUE_COLUMNS.size() + 1, "?")) + ")";

    /**
     * 반영할 행 — 실제 product 가 없거나, hash 가 다르거나, 원천에서 사라져 현행 아님이 됐다가 다시 나타난 행,
     * 또는 hash 는 같은데 같이 보이는 칩만 바뀐 행(칩 열이 있는 파일일 때만, 관리자가 고친 제품은 뺌).
     * 관리자가 비활성으로 돌린 제품은 건드리지 않는다.
     */
    private static final String CHANGED_SQL = """
            SELECT s.master_id,
                   p.id AS product_id,
                   (p.id IS NOT NULL AND p.is_current = FALSE) AS reactivate
            FROM   product_staging s
            LEFT JOIN products p ON p.master_id = s.master_id
            WHERE  p.id IS NULL
               OR  (p.is_active = TRUE AND p.content_hash IS DISTINCT FROM s.content_hash)
               OR  (p.is_active = TRUE AND p.is_current = FALSE)
               OR  (p.is_active = TRUE AND p.manually_corrected = FALSE AND s.extra_categories IS NOT NULL
                    AND ARRAY(SELECT x FROM unnest(string_to_array(s.extra_categories, '|')) AS x ORDER BY x)
                        IS DISTINCT FROM
                        ARRAY(SELECT c.code::text FROM product_category_chips pcc JOIN categories c ON c.id = pcc.category_id
                              WHERE pcc.product_id = p.id ORDER BY 1))
            ORDER BY s.master_id
            """;

    /**
     * 이번 대기 테이블이 맡는 출처 — 대기 테이블에 한 행이라도 있는 source.
     * 최신화는 이 출처의 제품만 현행 아님으로 돌린다. 다른 경로로 들어온 출처(올리브영 CSV 등)는 대기 테이블에 없으니 건드리지 않는다.
     * 출처 목록을 설정에 고정하지 않는 이유: 수집 실패로 한 출처가 통째로 빠지면 고정 목록은 그 출처 제품을 전부 현행 아님으로 돌리지만,
     * 이 방식은 그 출처를 아예 맡지 않는다.
     */
    private static final String MANAGED_SOURCES = "SELECT DISTINCT s2.source FROM product_staging s2 WHERE s2.source IS NOT NULL";

    /**
     * 원천에서 사라진 행 — 대기 테이블이 맡는 출처의, 적재로 들어온(master_id 있는) 활성 · 현행 제품 중 대기 테이블에 없는 것.
     * 관리자가 고친 제품은 건드리지 않는다. source 가 비어 있는 제품은 어느 출처에도 속하지 않아 대상이 아니다.
     */
    private static final String DISAPPEARED_SQL = """
            SELECT p.id, p.source
            FROM   products p
            WHERE  p.master_id IS NOT NULL
              AND  p.is_active = TRUE
              AND  p.is_current = TRUE
              AND  p.manually_corrected = FALSE
              AND  p.source IN (""" + MANAGED_SOURCES + """
            )
              AND  NOT EXISTS (SELECT 1 FROM product_staging s WHERE s.master_id = p.master_id)
            ORDER BY p.source, p.id
            """;

    /** 대기 테이블이 맡는 출처별 활성 적재 제품 수 — 출처별 대량 「현행 아님」 방지 기준. */
    private static final String ACTIVE_LOADED_BY_SOURCE_SQL = """
            SELECT p.source, COUNT(*) AS n
            FROM   products p
            WHERE  p.master_id IS NOT NULL
              AND  p.is_active = TRUE
              AND  p.source IN (""" + MANAGED_SOURCES + """
            )
            GROUP BY p.source
            """;

    public record ChangedRow(String masterId, Long productId, boolean reactivate) {}

    public record DisappearedRow(Long productId, String source) {}

    private final JdbcTemplate jdbcTemplate;

    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_staging", Long.class);
        return n == null ? 0 : n;
    }

    /** 복제 테이블을 통째로 비운다. 호출자가 트랜잭션을 연다(비우기와 채우기를 한 번에 커밋). */
    public void truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE product_staging");
    }

    /**
     * 행들을 넣는다. 각 맵은 VALUE_COLUMNS 를 키로 갖는다(없거나 빈 값은 NULL). 호출자가 트랜잭션을 연다.
     * extra_categories 만 빈 값 ""(칩 없음)을 그대로 넣는다 — NULL(파일에 열 없음)과 뜻이 다르다.
     */
    public void insertAll(List<Map<String, String>> rows, LocalDateTime computedAt) {
        Timestamp at = Timestamp.valueOf(computedAt);
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            for (Map<String, String> row : rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()))) {
                Object[] args = new Object[VALUE_COLUMNS.size() + 1];
                for (int i = 0; i < VALUE_COLUMNS.size(); i++) {
                    String col = VALUE_COLUMNS.get(i);
                    String v = row.get(col);
                    args[i] = v == null || (v.isEmpty() && !EXTRA_CATEGORIES.equals(col)) ? null : v;
                }
                args[VALUE_COLUMNS.size()] = at;
                batch.add(args);
            }
            jdbcTemplate.batchUpdate(INSERT_SQL, batch);
        }
    }

    public List<ChangedRow> findChanged() {
        return jdbcTemplate.query(CHANGED_SQL, (rs, i) -> new ChangedRow(
                rs.getString("master_id"),
                (Long) rs.getObject("product_id", Long.class),
                rs.getBoolean("reactivate")));
    }

    public List<DisappearedRow> findDisappeared() {
        return jdbcTemplate.query(DISAPPEARED_SQL, (rs, i) -> new DisappearedRow(rs.getLong("id"), rs.getString("source")));
    }

    /** 출처 → 활성 적재 제품 수 (대기 테이블이 맡는 출처만). */
    public Map<String, Long> countActiveLoadedBySource() {
        Map<String, Long> out = new HashMap<>();
        jdbcTemplate.query(ACTIVE_LOADED_BY_SOURCE_SQL, rs -> {
            out.put(rs.getString("source"), rs.getLong("n"));
        });
        return out;
    }

    /** master_id 들의 행을 「컬럼명 → 문자열 값」 맵으로. NULL 은 맵에 null 로 들어간다. */
    public Map<String, Map<String, String>> findRows(List<String> masterIds) {
        Map<String, Map<String, String>> out = new HashMap<>();
        if (masterIds.isEmpty()) return out;
        String sql = "SELECT " + String.join(", ", VALUE_COLUMNS) + " FROM product_staging WHERE master_id = ANY (?)";
        jdbcTemplate.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setArray(1, con.createArrayOf("varchar", masterIds.toArray()));
            return ps;
        }, rs -> {
            Map<String, String> row = new HashMap<>();
            for (String c : VALUE_COLUMNS) row.put(c, rs.getString(c));
            out.put(row.get("master_id"), row);
        });
        return out;
    }
}