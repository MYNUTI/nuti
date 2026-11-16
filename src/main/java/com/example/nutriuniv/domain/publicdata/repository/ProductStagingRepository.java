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

    /** product_staging 의 값 컬럼 — master_id + hash 대상 30칸 + content_hash. */
    public static final List<String> VALUE_COLUMNS;
    static {
        List<String> cols = new ArrayList<>();
        cols.add("master_id");
        cols.addAll(ContentHashCalculator.HASH_COLUMNS);
        cols.add("content_hash");
        VALUE_COLUMNS = List.copyOf(cols);
    }

    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT_SQL = "INSERT INTO product_staging (" + String.join(", ", VALUE_COLUMNS) + ", computed_at) VALUES ("
            + String.join(", ", Collections.nCopies(VALUE_COLUMNS.size() + 1, "?")) + ")";

    /**
     * 반영할 행 — 실제 product 가 없거나, hash 가 다르거나, 최신화가 비활성으로 돌린 뒤 다시 나타난 행.
     * 「최신화가 비활성으로 돌림」은 그 제품의 마지막 변경 이력이 SYNC_DEACTIVATE 인 것으로 가린다 —
     * 관리자가 직접 비활성으로 돌린 제품은 다시 살리지 않는다.
     */
    private static final String CHANGED_SQL = """
            SELECT s.master_id,
                   p.id AS product_id,
                   (p.id IS NOT NULL AND p.is_active = FALSE) AS reactivate
            FROM   product_staging s
            LEFT JOIN products p ON p.master_id = s.master_id
            WHERE  p.id IS NULL
               OR  (p.is_active = TRUE AND p.content_hash IS DISTINCT FROM s.content_hash)
               OR  (p.is_active = FALSE AND (SELECT l.source FROM product_change_logs l
                                              WHERE l.product_id = p.id
                                              ORDER BY l.created_at DESC, l.id DESC LIMIT 1) = 'SYNC_DEACTIVATE')
            ORDER BY s.master_id
            """;

    /** 원천에서 사라진 행 — 적재로 들어온(master_id 있는) 활성 제품 중 복제 테이블에 없는 것. 관리자가 고친 제품은 건드리지 않는다. */
    private static final String DISAPPEARED_SQL = """
            SELECT p.id
            FROM   products p
            WHERE  p.master_id IS NOT NULL
              AND  p.is_active = TRUE
              AND  p.manually_corrected = FALSE
              AND  NOT EXISTS (SELECT 1 FROM product_staging s WHERE s.master_id = p.master_id)
            ORDER BY p.id
            """;

    public record ChangedRow(String masterId, Long productId, boolean reactivate) {}

    private final JdbcTemplate jdbcTemplate;

    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_staging", Long.class);
        return n == null ? 0 : n;
    }

    /** 복제 테이블을 통째로 비운다. 호출자가 트랜잭션을 연다(비우기와 채우기를 한 번에 커밋). */
    public void truncate() {
        jdbcTemplate.execute("TRUNCATE TABLE product_staging");
    }

    /** 행들을 넣는다. 각 맵은 VALUE_COLUMNS 를 키로 갖는다(없거나 빈 값은 NULL). 호출자가 트랜잭션을 연다. */
    public void insertAll(List<Map<String, String>> rows, LocalDateTime computedAt) {
        Timestamp at = Timestamp.valueOf(computedAt);
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            for (Map<String, String> row : rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()))) {
                Object[] args = new Object[VALUE_COLUMNS.size() + 1];
                for (int i = 0; i < VALUE_COLUMNS.size(); i++) {
                    String v = row.get(VALUE_COLUMNS.get(i));
                    args[i] = v == null || v.isEmpty() ? null : v;
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

    public List<Long> findDisappearedProductIds() {
        return jdbcTemplate.queryForList(DISAPPEARED_SQL, Long.class);
    }

    /** 적재로 들어온 활성 제품 수 — 대량 비활성 방지 기준. */
    public long countActiveLoadedProducts() {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE master_id IS NOT NULL AND is_active = TRUE", Long.class);
        return n == null ? 0 : n;
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