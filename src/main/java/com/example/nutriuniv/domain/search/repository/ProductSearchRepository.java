package com.example.nutriuniv.domain.search.repository;

import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 4-way 제품 검색 SQL (기능명세서 6.1). 전부 사전 색인 컬럼(name·name_normalized·name_chosung)과 pg_trgm 만 쓴다.
 * <ul>
 *   <li>정확 일치 = 이름 그대로 포함 ∪ 공백·특수문자 제거 대조 ∪ 초성 ∪ 브랜드명</li>
 *   <li>비슷한 제품 = 정확 일치 제외 ∩ (철자 유사도 ≥ 기준값 ∪ 동의어 포함)</li>
 *   <li>정렬 = 등급순(점수 내림차순), 영양정보 부족(등급 없음)은 맨 뒤</li>
 * </ul>
 * GIN(trgm) 인덱스는 db/manual/06_search.sql — 없어도 동작하고 있으면 빨라진다.
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Predicate(String sql, List<Object> args) {}

    public record Candidate(long productId, String name, String nameNormalized, Long categoryId, double similarity) {}

    public record SuggestRow(long productId, String name, String brandName) {}

    private static final String FROM = """
            FROM   products p
            LEFT JOIN brands b ON b.id = p.brand_id
            """;

    // ── 조건 ─────────────────────────────────────────────────────────────────────

    public Predicate exactPredicate(String raw, String normalized, String chosung) {
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String rawLike = "%" + SearchNormalizer.escapeLike(raw) + "%";
        parts.add("p.name ILIKE ?");             args.add(rawLike);
        parts.add("b.name ILIKE ?");             args.add(rawLike);
        parts.add("p.name_normalized LIKE ?");   args.add("%" + SearchNormalizer.escapeLike(normalized) + "%");
        if (chosung != null && !chosung.isEmpty()) {
            parts.add("p.name_chosung LIKE ?");  args.add("%" + SearchNormalizer.escapeLike(chosung) + "%");
        }
        return new Predicate("(" + String.join(" OR ", parts) + ")", args);
    }

    /**
     * 검색 묶음 (SRCH_001) — 정확 · 부분 일치 OR 비슷한 이름(pg_trgm %) OR 사전 동의어. 한 목록으로 점수순에 섞는다.
     * % 연산자는 set_limit 값을 쓴다 — 호출 전에 {@link #setSimilarityLimit} (같은 트랜잭션 · 연결).
     */
    public ProductListQuery.Scope matchScope(Predicate exact, String normalized, List<String> synonyms) {
        List<String> parts = new ArrayList<>(List.of(exact.sql(), "p.name_normalized % ?"));
        List<Object> args = new ArrayList<>(exact.args());
        args.add(normalized);
        for (String syn : synonyms) {
            parts.add("p.name_normalized LIKE ?");
            args.add("%" + SearchNormalizer.escapeLike(syn) + "%");
        }
        return new ProductListQuery.Scope("(" + String.join(" OR ", parts) + ")", args);
    }

    public void setSimilarityLimit(double threshold) {
        jdbcTemplate.queryForObject("SELECT set_limit(?)", Float.class, (float) threshold);
    }

    /** 검색어가 제품 이름 전체와 같은 제품 (공백 · 특수문자 무시). 여럿이면 점수 높은 것. */
    public Optional<Long> pinnedId(String normalized, GoalType goal, int slot) {
        String sql = """
                SELECT p.id FROM products p
                LEFT JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = ? AND pg.eer_band = ?
                WHERE p.is_active = TRUE AND p.name_normalized = ?
                ORDER BY (pg.score IS NULL), pg.score DESC, p.id LIMIT 1
                """;
        return jdbcTemplate.query(sql, (rs, i) -> rs.getLong(1), goal.name(), slot, normalized).stream().findFirst();
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────────

    public long count(Predicate where) {
        String sql = "SELECT COUNT(*) " + FROM + "WHERE p.is_active = TRUE AND " + where.sql();
        Long n = jdbcTemplate.queryForObject(sql, Long.class, where.args().toArray());
        return n == null ? 0 : n;
    }

    /** 유사도 최상위 1개 (기준값 무관) — didYouMean 후보·「같은 분류 상위」의 분류를 정할 때. */
    public Optional<Candidate> bestSimilar(String normalized) {
        String sql = """
                SELECT p.id, p.name, p.name_normalized, p.category_id, similarity(p.name_normalized, ?) AS sim
                FROM   products p
                WHERE  p.is_active = TRUE AND p.name_normalized IS NOT NULL
                ORDER BY sim DESC, p.view_count DESC
                LIMIT 1
                """;
        List<Candidate> rows = jdbcTemplate.query(sql, (rs, i) -> new Candidate(
                rs.getLong("id"), rs.getString("name"), rs.getString("name_normalized"),
                rs.getObject("category_id") == null ? null : rs.getLong("category_id"), rs.getDouble("sim")
        ), normalized);
        return rows.stream().findFirst();
    }

    /** 분류 안 등급 상위 (분석 완료만) — 0건 폴백 「같은 분류 상위 5개」. */
    public List<Long> topInCategory(Long categoryId, GoalType goal, int slot, int limit) {
        String sql = """
                SELECT p.id
                FROM   products p
                JOIN   product_grades pg ON pg.product_id = p.id AND pg.goal = ? AND pg.eer_band = ?
                WHERE  p.is_active = TRUE AND p.category_id = ? AND p.status = 'ANALYZED' AND p.is_discontinued = FALSE
                ORDER BY pg.score DESC, p.name, p.id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, i) -> rs.getLong("id"), goal.name(), slot, categoryId, limit);
    }

    // ── 자동완성 ──────────────────────────────────────────────────────────────────

    /** 접두 일치를 먼저, 그다음 포함 — name_normalized(공백 무시)·name_chosung(초성). */
    public List<SuggestRow> suggest(String normalized, String chosung, int limit) {
        String n = SearchNormalizer.escapeLike(normalized);
        List<String> where = new ArrayList<>();
        List<String> prefixCases = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        // SELECT 절의 CASE(접두) 가 WHERE 앞에 오므로 인자 순서: 접두 패턴들 → 포함 패턴들 → limit
        prefixCases.add("WHEN p.name_normalized LIKE ? THEN 0");
        args.add(n + "%");
        if (chosung != null && !chosung.isEmpty()) {
            prefixCases.add("WHEN p.name_chosung LIKE ? THEN 0");
            args.add(SearchNormalizer.escapeLike(chosung) + "%");
        }
        where.add("p.name_normalized LIKE ?");
        args.add("%" + n + "%");
        if (chosung != null && !chosung.isEmpty()) {
            where.add("p.name_chosung LIKE ?");
            args.add("%" + SearchNormalizer.escapeLike(chosung) + "%");
        }
        args.add(limit);

        String sql = "SELECT p.id, p.name, b.name AS brand_name, CASE " + String.join(" ", prefixCases) + " ELSE 1 END AS prio "
                + FROM
                + "WHERE p.is_active = TRUE AND (" + String.join(" OR ", where) + ") "
                + "ORDER BY prio, p.view_count DESC, p.id LIMIT ?";
        return jdbcTemplate.query(sql, (rs, i) -> new SuggestRow(rs.getLong("id"), rs.getString("name"), rs.getString("brand_name")),
                args.toArray());
    }

    /** 오타 보정 — 철자 유사도순. */
    public List<SuggestRow> suggestSimilar(String normalized, double threshold, int limit) {
        String sql = "SELECT p.id, p.name, b.name AS brand_name, similarity(p.name_normalized, ?) AS sim "
                + FROM
                + "WHERE p.is_active = TRUE AND similarity(p.name_normalized, ?) >= ? "
                + "ORDER BY sim DESC, p.view_count DESC, p.id LIMIT ?";
        return jdbcTemplate.query(sql, (rs, i) -> new SuggestRow(rs.getLong("id"), rs.getString("name"), rs.getString("brand_name")),
                normalized, normalized, threshold, limit);
    }
}
