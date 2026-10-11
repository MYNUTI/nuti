package com.example.nutriuniv.domain.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 관리자 지표 집계 SQL — 재방문(9.2, 모수=동의한 사용자)·데이터 현황(10.5). */
@Repository
@RequiredArgsConstructor
public class AdminMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    public record RetentionCounts(long baseCount, long revisitCount) {}

    public record DataTotals(long totalProducts, long analyzedCount, long barcodeCount, LocalDateTime lastLoadedAt) {}

    public record CategoryRow(long categoryId, String name, long totalCount, long analyzedCount, long aCount, long dCount) {}

    /**
     * 모수 = 0~6일차에 유효 방문한 「동의한 사용자」(anonymous_users 에 있는 익명 ID) 중 첫 방문이 [from, to) 인 사람.
     * 재방문 = 그중 7~27일차에 유효 방문 1회 이상. 1차 클라 생성 UUID 세션은 anonymous_users 에 없어 자연히 빠진다.
     */
    public RetentionCounts retention(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        String sql = """
                SELECT COUNT(DISTINCT base.anonymous_id) AS base_count,
                       COUNT(DISTINCT ret.anonymous_id)  AS revisit_count
                FROM (
                    SELECT v.anonymous_id, MIN(v.session_start) AS first_start
                    FROM   visit_sessions v
                    JOIN   anonymous_users a ON a.anonymous_id = v.anonymous_id
                    WHERE  v.qualified = TRUE AND v.days_since_first BETWEEN 0 AND 6
                    GROUP BY v.anonymous_id
                ) base
                LEFT JOIN (
                    SELECT DISTINCT anonymous_id
                    FROM   visit_sessions
                    WHERE  qualified = TRUE AND days_since_first BETWEEN 7 AND 27
                ) ret ON ret.anonymous_id = base.anonymous_id
                WHERE  base.first_start >= ? AND base.first_start < ?
                """;
        return jdbcTemplate.queryForObject(sql, (rs, i) -> new RetentionCounts(rs.getLong("base_count"), rs.getLong("revisit_count")),
                Timestamp.valueOf(fromInclusive), Timestamp.valueOf(toExclusive));
    }

    public DataTotals totals() {
        String sql = """
                SELECT COUNT(*) FILTER (WHERE is_active)                              AS total_products,
                       COUNT(*) FILTER (WHERE is_active AND status = 'ANALYZED')      AS analyzed_count,
                       COUNT(*) FILTER (WHERE is_active AND barcode IS NOT NULL)      AS barcode_count,
                       MAX(updated_at)                                                AS last_loaded_at
                FROM   products
                """;
        return jdbcTemplate.queryForObject(sql, (rs, i) -> new DataTotals(
                rs.getLong("total_products"), rs.getLong("analyzed_count"), rs.getLong("barcode_count"),
                rs.getTimestamp("last_loaded_at") == null ? null : rs.getTimestamp("last_loaded_at").toLocalDateTime()));
    }

    /** 일반 기준 등급 분포 (A~E). */
    public Map<String, Long> gradeDistribution() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String g : List.of("A", "B", "C", "D", "E")) out.put(g, 0L);
        jdbcTemplate.query("""
                SELECT CAST(grade AS VARCHAR) AS grade, COUNT(*) AS cnt
                FROM   product_grades
                WHERE  goal = 'GENERAL' AND eer_band = 0
                GROUP BY grade
                """, rs -> { out.put(rs.getString("grade"), rs.getLong("cnt")); });
        return out;
    }

    /** 대분류(parent 없음)별 제품 수·분석 완료 수·A/D 수 — 랭킹 게이트(300건 + A·D 각 1) 판정용. 하위 분류를 모두 합친다. */
    public List<CategoryRow> categoryAnalyzed() {
        String sql = """
                WITH RECURSIVE tree AS (
                    SELECT id, id AS root_id FROM categories WHERE parent_id IS NULL
                    UNION ALL
                    SELECT c.id, t.root_id FROM categories c JOIN tree t ON c.parent_id = t.id
                )
                SELECT r.id AS category_id, r.name,
                       COUNT(p.id)                                        AS total_count,
                       COUNT(p.id) FILTER (WHERE p.status = 'ANALYZED')   AS analyzed_count,
                       COUNT(pg.product_id) FILTER (WHERE pg.grade = 'A') AS a_count,
                       COUNT(pg.product_id) FILTER (WHERE pg.grade = 'D') AS d_count
                FROM   categories r
                JOIN   tree t ON t.root_id = r.id
                LEFT JOIN products p ON p.category_id = t.id AND p.is_active = TRUE
                LEFT JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = 'GENERAL' AND pg.eer_band = 0
                WHERE  r.parent_id IS NULL
                GROUP BY r.id, r.name
                ORDER BY total_count DESC, r.id
                """;
        return jdbcTemplate.query(sql, (rs, i) -> new CategoryRow(rs.getLong("category_id"), rs.getString("name"),
                rs.getLong("total_count"), rs.getLong("analyzed_count"), rs.getLong("a_count"), rs.getLong("d_count")));
    }

    public long pendingAnalysisRequests() {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analysis_requests WHERE status IN ('WAITING', 'PROCESSING')", Long.class);
        return n == null ? 0 : n;
    }
}
