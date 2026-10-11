package com.example.nutriuniv.domain.ranking.repository;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 랭킹 배치 SQL (기능명세서 6.3). 서비스 분류 ↔ 공공 분류 매핑을 재귀 CTE 로 풀어 제품의 분류마다
 * <b>가장 가까운 매핑</b>(depth 최소)을 고른다 — 대분류를 A 에, 그 아래 한 중분류만 B 에 매핑하는 식의 지정이 가능하다.
 * <p>순위는 목표별 사전계산 등급(product_grades)의 점수 내림차순, 동점은 분석 최신순(product_nutrients.updated_at), 그다음 id.
 * 분석 완료(status=ANALYZED) 제품만 순위에 들고, 분모(total_count)에는 영양정보 부족 제품도 들어간다.
 */
@Repository
@RequiredArgsConstructor
public class RankingBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public record CategoryStatRow(long rankingCategoryId, long totalCount, long analyzedCount, long aCount, long dCount) {}

    public record RankRow(long rankingCategoryId, int rank, long productId, BigDecimal score, String grade) {}

    /** 활성 서비스 분류의 매핑을 하위 분류까지 펼치고, categories 행마다 가장 가까운 매핑 하나로 확정한다. */
    static final String RESOLVED_CTE = """
            WITH RECURSIVE mapped AS (
                SELECT m.ranking_category_id, m.category_id AS id, 0 AS depth
                FROM   ranking_category_mappings m
                JOIN   ranking_categories rc ON rc.id = m.ranking_category_id AND rc.is_active = TRUE
                UNION ALL
                SELECT t.ranking_category_id, c.id, t.depth + 1
                FROM   categories c
                JOIN   mapped t ON c.parent_id = t.id
            ),
            resolved AS (
                SELECT DISTINCT ON (id) id AS category_id, ranking_category_id
                FROM   mapped
                ORDER  BY id, depth
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO category_rankings (ranking_category_id, goal, rank_no, product_id, grade, score, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPSERT_STAT_SQL = """
            INSERT INTO ranking_category_stats
                (ranking_category_id, total_count, analyzed_count, a_count, d_count, gate_passed, ranked_rows, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (ranking_category_id) DO UPDATE
               SET total_count    = EXCLUDED.total_count,
                   analyzed_count = EXCLUDED.analyzed_count,
                   a_count        = EXCLUDED.a_count,
                   d_count        = EXCLUDED.d_count,
                   gate_passed    = EXCLUDED.gate_passed,
                   ranked_rows    = EXCLUDED.ranked_rows,
                   computed_at    = EXCLUDED.computed_at
            """;

    // ── 읽기 ─────────────────────────────────────────────────────────────────────

    /** 서비스 분류별 분모·분석 완료·A/D(일반 기준) — 게이트 판정 입력. 매핑된 제품이 하나도 없는 분류는 행이 없다. */
    public List<CategoryStatRow> categoryStats() {
        String sql = RESOLVED_CTE + """
                SELECT r.ranking_category_id,
                       COUNT(p.id)                                        AS total_count,
                       COUNT(p.id) FILTER (WHERE p.status = 'ANALYZED')   AS analyzed_count,
                       COUNT(pg.product_id) FILTER (WHERE pg.grade = 'A') AS a_count,
                       COUNT(pg.product_id) FILTER (WHERE pg.grade = 'D') AS d_count
                FROM   resolved r
                JOIN   products p ON p.category_id = r.category_id AND p.is_active = TRUE
                LEFT JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = 'GENERAL' AND pg.eer_band = 0
                GROUP BY r.ranking_category_id
                """;
        return jdbcTemplate.query(sql, (rs, i) -> new CategoryStatRow(
                rs.getLong("ranking_category_id"), rs.getLong("total_count"), rs.getLong("analyzed_count"),
                rs.getLong("a_count"), rs.getLong("d_count")));
    }

    /**
     * 한 목표의 전 분류 순위를 한 번에 — 창 함수로 분류마다 상위 maxRank 까지. 분류 수만큼 쿼리를 반복하지 않는다(products 스캔 1회).
     */
    public List<RankRow> rankAll(GoalType goal, int slot, int maxRank) {
        String sql = RESOLVED_CTE + """
                , ranked AS (
                    SELECT r.ranking_category_id, p.id AS product_id, pg.score, CAST(pg.grade AS VARCHAR) AS grade,
                           ROW_NUMBER() OVER (PARTITION BY r.ranking_category_id
                                              ORDER BY pg.score DESC, pn.updated_at DESC NULLS LAST, p.id) AS rank_no
                    FROM   resolved r
                    JOIN   products p          ON p.category_id = r.category_id AND p.is_active = TRUE AND p.status = 'ANALYZED'
                    JOIN   product_grades pg   ON pg.product_id = p.id AND pg.goal = ? AND pg.eer_band = ?
                    LEFT JOIN product_nutrients pn ON pn.product_id = p.id
                )
                SELECT ranking_category_id, rank_no, product_id, score, grade
                FROM   ranked
                WHERE  rank_no <= ?
                ORDER  BY ranking_category_id, rank_no
                """;
        return jdbcTemplate.query(sql, (rs, i) -> new RankRow(
                rs.getLong("ranking_category_id"), rs.getInt("rank_no"), rs.getLong("product_id"),
                rs.getBigDecimal("score"), rs.getString("grade")), goal.name(), slot, maxRank);
    }

    // ── 쓰기 (호출자가 트랜잭션을 연다) ───────────────────────────────────────────

    /** 분류×목표의 순위를 통째로 교체. 조회는 같은 트랜잭션이 끝날 때까지 직전 값을 본다(MVCC). */
    public int replaceRankings(long rankingCategoryId, GoalType goal, List<RankRow> rows, LocalDateTime computedAt) {
        jdbcTemplate.update("DELETE FROM category_rankings WHERE ranking_category_id = ? AND goal = ?",
                rankingCategoryId, goal.name());
        if (rows.isEmpty()) return 0;
        Timestamp ts = Timestamp.valueOf(computedAt);
        List<Object[]> args = new ArrayList<>(rows.size());
        for (RankRow r : rows) {
            args.add(new Object[]{rankingCategoryId, goal.name(), r.rank(), r.productId(), r.grade(), r.score(), ts});
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, args);
        return rows.size();
    }

    /** 게이트 미통과·비활성 분류 — 남아 있던 순위 삭제. */
    public int deleteRankings(long rankingCategoryId) {
        return jdbcTemplate.update("DELETE FROM category_rankings WHERE ranking_category_id = ?", rankingCategoryId);
    }

    /** 비활성화된(또는 삭제된) 서비스 분류의 잔여 순위·메타 정리. */
    public int purgeInactive() {
        int a = jdbcTemplate.update("""
                DELETE FROM category_rankings cr
                WHERE  NOT EXISTS (SELECT 1 FROM ranking_categories rc WHERE rc.id = cr.ranking_category_id AND rc.is_active = TRUE)
                """);
        int b = jdbcTemplate.update("""
                DELETE FROM ranking_category_stats s
                WHERE  NOT EXISTS (SELECT 1 FROM ranking_categories rc WHERE rc.id = s.ranking_category_id AND rc.is_active = TRUE)
                """);
        return a + b;
    }

    public void upsertStat(long rankingCategoryId, CategoryStatRow stat, boolean gatePassed, int rankedRows, LocalDateTime computedAt) {
        jdbcTemplate.update(UPSERT_STAT_SQL, rankingCategoryId,
                stat.totalCount(), stat.analyzedCount(), stat.aCount(), stat.dCount(),
                gatePassed, rankedRows, Timestamp.valueOf(computedAt));
    }
}
