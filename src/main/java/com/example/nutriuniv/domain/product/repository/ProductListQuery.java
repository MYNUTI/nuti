package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.product.enums.FilterChip;
import com.example.nutriuniv.domain.product.enums.ListSort;
import com.example.nutriuniv.domain.product.enums.Seller;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 제품 목록 조회 공용 SQL (검색 · 랭킹 · 더 맞는 제품 · 필터 개수). 조회할 때 계산한다 — 필터 조합이 자유로워 미리 만든 순위 표로는 안 된다.
 * <ul>
 *   <li>Scope: 어떤 제품 묶음인가 (검색어 · 칩 · 중분류)</li>
 *   <li>rankable: 순위에 드는 제품만 — 분석 완료 · 생산 중 (등급 없음 · 단종 · 생산 중단 제외)</li>
 *   <li>필터: 탭끼리는 AND, 탭 안 값끼리는 OR (SRCH_008)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class ProductListQuery {

    /** 화면 브랜드 줄 — 브랜드, 없으면 「제조 ○○」 (ProductResultService.brandLine 과 같은 값). */
    public static final String BRAND_LINE = "COALESCE(NULLIF(TRIM(b.name), ''), '제조 ' || NULLIF(TRIM(p.maker), ''))";

    private static final String FROM = """
             FROM products p
             LEFT JOIN brands b ON b.id = p.brand_id
             LEFT JOIN product_nutrients pn ON pn.product_id = p.id
             LEFT JOIN coupang_links cl ON cl.product_id = p.id
            """;
    private static final String RANKABLE = "p.status = 'ANALYZED' AND p.is_discontinued = FALSE";

    private final JdbcTemplate jdbcTemplate;

    public record Scope(String sql, List<Object> args) {
        public static Scope all() {
            return new Scope("TRUE", List.of());
        }

        /** 칩(중분류 · 합산 칩)에 보이는 제품 — 그 중분류 제품 + 「같이 보이는 곳」으로 그 칩을 단 제품. */
        public static Scope chip(long categoryId) {
            return new Scope("(p.category_id = ? OR EXISTS (SELECT 1 FROM product_category_chips pcc"
                    + " WHERE pcc.product_id = p.id AND pcc.category_id = ?))", List.of(categoryId, categoryId));
        }

        /** 대분류 전체 — 그 아래 칩들 중 하나에라도 보이는 제품. */
        public static Scope major(long majorId) {
            return new Scope("(p.category_id IN (SELECT c.id FROM categories c WHERE c.parent_id = ?)"
                    + " OR EXISTS (SELECT 1 FROM product_category_chips pcc JOIN categories cc ON cc.id = pcc.category_id"
                    + " WHERE pcc.product_id = p.id AND cc.parent_id = ?))", List.of(majorId, majorId));
        }

        /** 같은 중분류 (더 맞는 제품). */
        public static Scope sameCategory(long categoryId) {
            return new Scope("p.category_id = ?", List.of(categoryId));
        }

        public Scope and(String sql, List<Object> args) {
            List<Object> a = new ArrayList<>(this.args);
            a.addAll(args);
            return new Scope("(" + this.sql + ") AND (" + sql + ")", a);
        }
    }

    public record Filters(List<String> brands, List<FilterChip> nutrients, List<Seller> sellers) {
        public static final Filters NONE = new Filters(List.of(), List.of(), List.of());

        public Filters {
            brands = brands == null ? List.of() : List.copyOf(brands);
            nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
            sellers = sellers == null ? List.of() : List.copyOf(sellers);
        }

        public boolean isEmpty() {
            return brands.isEmpty() && nutrients.isEmpty() && sellers.isEmpty();
        }
    }

    public record Page(List<Long> ids, long total) {}

    public record BrandCount(String name, boolean confirmed, long count) {}

    // ── 목록 ─────────────────────────────────────────────────────────────────────

    public long count(Scope scope, boolean rankable, Filters f) {
        return count(scope, rankable, f, null);
    }

    public long count(Scope scope, boolean rankable, Filters f, Long excludeId) {
        List<Object> args = new ArrayList<>();
        String where = where(scope, rankable, f, excludeId, args);
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*)" + FROM + "WHERE " + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public Page page(Scope scope, boolean rankable, Filters f, GoalType goal, int slot, ListSort sort,
                     Long excludeId, int limit, int offset) {
        List<Object> args = new ArrayList<>(List.of(goal.name(), slot));
        String where = where(scope, rankable, f, excludeId, args);
        args.add(limit);
        args.add(offset);
        String sql = "SELECT p.id" + FROM
                + " LEFT JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = ? AND pg.eer_band = ?"
                + " WHERE " + where
                + " ORDER BY " + sort.orderBy()
                + " LIMIT ? OFFSET ?";
        List<Long> ids = jdbcTemplate.query(sql, (rs, i) -> rs.getLong(1), args.toArray());
        long total = (offset == 0 && ids.size() < limit) ? ids.size() : count(scope, rankable, f, excludeId);
        return new Page(ids, total);
    }

    // ── 필터 시트 (SRCH_008) ──────────────────────────────────────────────────────

    /** 순위에 드는 제품 중 칩별 해당 개수 + 전체(키 null 대신 total 인자). */
    public Map<FilterChip, Long> chipCounts(Scope scope, long[] totalOut) {
        StringBuilder sel = new StringBuilder("SELECT COUNT(*)");
        for (FilterChip c : FilterChip.values()) {
            sel.append(", COUNT(*) FILTER (WHERE ").append(c.sql()).append(")");
        }
        List<Object> args = new ArrayList<>();
        String where = where(scope, true, Filters.NONE, null, args);
        Map<FilterChip, Long> out = new EnumMap<>(FilterChip.class);
        jdbcTemplate.query(sel + FROM + "WHERE " + where, rs -> {
            totalOut[0] = rs.getLong(1);
            int i = 2;
            for (FilterChip c : FilterChip.values()) out.put(c, rs.getLong(i++));
        }, args.toArray());
        return out;
    }

    /** 브랜드 줄별 제품 수 (순위에 드는 제품). confirmed = 브랜드 사전에 있는 이름(「제조 ○○」 아님). */
    public List<BrandCount> brandCounts(Scope scope) {
        List<Object> args = new ArrayList<>();
        String where = where(scope, true, Filters.NONE, null, args);
        String sql = "SELECT " + BRAND_LINE + " AS line, (NULLIF(TRIM(b.name), '') IS NOT NULL) AS confirmed, COUNT(*) AS n"
                + FROM + "WHERE " + where + " AND " + BRAND_LINE + " IS NOT NULL"
                + " GROUP BY 1, 2 ORDER BY 3 DESC, 1";
        return jdbcTemplate.query(sql, (rs, i) -> new BrandCount(rs.getString(1), rs.getBoolean(2), rs.getLong(3)), args.toArray());
    }

    /** 판매처별 제품 수. 자료가 없는 판매처는 0. */
    public Map<Seller, Long> sellerCounts(Scope scope, boolean rankable) {
        Map<Seller, Long> out = new EnumMap<>(Seller.class);
        for (Seller s : Seller.values()) {
            out.put(s, s.available() ? count(scope, rankable, new Filters(null, null, List.of(s))) : 0L);
        }
        return out;
    }

    // ── 더 맞는 제품 (COMP_001) ───────────────────────────────────────────────────

    /** 제품마다 같은 중분류에서 그 목표 점수가 더 높은 순위 대상 제품 수. 점수 · 중분류가 없는 제품은 키가 없다. */
    public Map<Long, Long> betterCounts(Collection<Long> ids, GoalType goal, int slot) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(goal.name(), slot, goal.name(), slot));
        args.addAll(ids);
        String sql = """
                SELECT x.id,
                       (SELECT COUNT(*) FROM products p
                          JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = ? AND pg.eer_band = ?
                         WHERE p.category_id = x.category_id AND p.is_active = TRUE AND %s AND pg.score > xg.score) AS better
                  FROM products x
                  JOIN product_grades xg ON xg.product_id = x.id AND xg.goal = ? AND xg.eer_band = ?
                 WHERE x.category_id IS NOT NULL AND x.id IN (%s)
                """.formatted(RANKABLE, in);
        Map<Long, Long> out = new HashMap<>();
        jdbcTemplate.query(sql, rs -> { out.put(rs.getLong(1), rs.getLong(2)); }, args.toArray());
        return out;
    }

    /** 같은 중분류에서 점수가 score 보다 높은 순위 대상 제품 — 점수 높은 순 → 이름순. */
    public Page better(long categoryId, GoalType goal, int slot, java.math.BigDecimal score, long excludeId, int limit, int offset) {
        String base = " FROM products p JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = ? AND pg.eer_band = ?"
                + " WHERE p.is_active = TRUE AND " + RANKABLE + " AND p.category_id = ? AND p.id <> ? AND pg.score > ?";
        Object[] args = {goal.name(), slot, categoryId, excludeId, score};
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + base, Long.class, args);
        List<Object> pageArgs = new ArrayList<>(List.of(args));
        pageArgs.add(limit);
        pageArgs.add(offset);
        List<Long> ids = jdbcTemplate.query("SELECT p.id" + base + " ORDER BY pg.score DESC, p.name, p.id LIMIT ? OFFSET ?",
                (rs, i) -> rs.getLong(1), pageArgs.toArray());
        return new Page(ids, total == null ? 0 : total);
    }

    // ── 조건 ─────────────────────────────────────────────────────────────────────

    private static String where(Scope scope, boolean rankable, Filters f, Long excludeId, List<Object> args) {
        List<String> parts = new ArrayList<>();
        parts.add("p.is_active = TRUE");
        parts.add("(" + scope.sql() + ")");
        args.addAll(scope.args());
        if (rankable) parts.add(RANKABLE);
        if (excludeId != null) {
            parts.add("p.id <> ?");
            args.add(excludeId);
        }
        if (!f.brands().isEmpty()) {
            parts.add(BRAND_LINE + " IN (" + String.join(",", Collections.nCopies(f.brands().size(), "?")) + ")");
            args.addAll(f.brands());
        }
        if (!f.nutrients().isEmpty()) {
            parts.add("(" + String.join(" OR ", f.nutrients().stream().map(FilterChip::sql).toList()) + ")");
        }
        if (!f.sellers().isEmpty()) {
            parts.add("(" + String.join(" OR ", f.sellers().stream().map(Seller::sql).toList()) + ")");
        }
        return String.join(" AND ", parts);
    }
}
