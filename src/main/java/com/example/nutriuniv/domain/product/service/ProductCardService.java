package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.calc.GradeFormula;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.grade.service.CalibrationService;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.entity.ProductState;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.enums.ListSort;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.product.util.ServingSize;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 목록 카드 조립 (검색 · 랭킹 · 기록 · 즐겨찾기 · 더 맞는 제품 · 비교) — 한 번에 읽어 N+1 없이.
 * 등급은 사전계산(product_grades)을 먼저 쓰고, 없으면(배치 전) 현행 기준으로 즉석 계산한다.
 */
@Service
@RequiredArgsConstructor
public class ProductCardService {

    private final JdbcTemplate jdbcTemplate;
    private final CalibrationService calibrationService;
    private final SavedProductRepository savedProductRepository;
    private final ProductListQuery listQuery;

    /** 카드를 만들 때 읽는 한 줄 — 비교 · 더 맞는 제품이 성분값을 다시 쓴다. */
    public record Row(long id, String name, String imageUrl, String brandLine, boolean discontinued,
                      String linkStatus, String affiliateUrl, LocalDateTime lastSyncedAt,
                      Long categoryId, String servingSize, Map<Nutrient, BigDecimal> per100g) {

        public ProductStatus status() {
            for (Nutrient n : Nutrient.values()) {
                if (n != Nutrient.DIETARY_FIBER && per100g.get(n) == null) return ProductStatus.INSUFFICIENT;
            }
            return ProductStatus.ANALYZED;
        }

        public String basis() {
            return ServingSize.basisOf(ServingSize.parse(servingSize));
        }
    }

    private record GradeRow(BigDecimal score, Grade grade) {}

    // ── 공개 ─────────────────────────────────────────────────────────────────────

    /** ids 순서를 지킨 카드 목록. 없는 · 비활성 제품은 빠진다. */
    public List<ProductCard> cards(List<Long> ids, GoalType goal, EerBand band, Owner owner, ListSort sort) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Row> rows = rows(ids);
        Map<Long, Map<GoalType, GradeRow>> grades = grades(rows.keySet(), band);
        Set<Long> saved = savedIds(owner);
        Calibration cal = calibrationService.current();
        double trans = transThreshold(cal);

        List<ProductCard> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Row r = rows.get(id);
            if (r == null) continue;
            out.add(card(r, grades.getOrDefault(id, Map.of()), goal, band, cal, trans, saved.contains(id), sort));
        }
        return out;
    }

    /** 카드에 「더 맞는 제품」 판정을 붙인다 (기록 · 즐겨찾기 · 결과). */
    public List<ProductCard> withBetter(List<ProductCard> cards, GoalType goal, EerBand band) {
        List<Long> scored = cards.stream().filter(c -> c.grade(goal.name()) != null).map(ProductCard::productId).toList();
        Map<Long, Long> counts = listQuery.betterCounts(scored, goal, EerBand.slot(goal, band));
        return cards.stream().map(c -> {
            ProductCard.GoalGrade g = c.grade(goal.name());
            if (g == null) return c;
            return c.withBetter(better(g.grade(), counts.getOrDefault(c.productId(), 0L)));
        }).toList();
    }

    public static ProductCard.BetterChoice better(String grade, long count) {
        if ("A".equals(grade) || "B".equals(grade)) return new ProductCard.BetterChoice("TOP", count);
        return new ProductCard.BetterChoice(count > 0 ? "AVAILABLE" : "NONE", count);
    }

    /** 성분값 행 (비교 · 더 맞는 제품의 100g 차이). */
    public Map<Long, Row> rows(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT p.id, p.name, p.image_url, p.is_discontinued, p.category_id, " + ProductListQuery.BRAND_LINE + " AS brand_line,"
                + " cl.link_status, cl.affiliate_url, cl.last_synced_at, pn.serving_size,"
                + " pn.calories_per_100g, pn.protein_per_100g, pn.dietary_fiber_per_100g, pn.sugar_per_100g,"
                + " pn.saturated_fat_per_100g, pn.trans_fat_per_100g, pn.cholesterol_per_100g, pn.sodium_per_100g"
                + " FROM products p"
                + " LEFT JOIN brands b ON b.id = p.brand_id"
                + " LEFT JOIN product_nutrients pn ON pn.product_id = p.id"
                + " LEFT JOIN coupang_links cl ON cl.product_id = p.id"
                + " WHERE p.is_active = TRUE AND p.id IN (" + in + ")";
        Map<Long, Row> out = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Row r = toRow(rs);
            out.put(r.id(), r);
        }, ids.toArray());
        return out;
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private ProductCard card(Row r, Map<GoalType, GradeRow> grades, GoalType goal, EerBand band,
                             Calibration cal, double trans, boolean saved, ListSort sort) {
        ProductStatus status = r.status();
        ProductState state = ProductState.of(status, r.discontinued(), r.linkStatus(), r.affiliateUrl(), r.lastSyncedAt());
        List<ProductCard.GoalGrade> goals = new ArrayList<>(2);
        List<String> tags = List.of();
        if (status == ProductStatus.ANALYZED) {
            GradeFormula.Input input = input(r.per100g());
            for (GoalType g : GoalType.values()) {
                int slot = EerBand.slot(g, band);
                GradeRow gr = grades.get(g);
                if (gr == null) {                                   // 배치 전 — 즉석 계산
                    GradeFormula.Result res = GradeFormula.evaluate(cal, g, slot, input);
                    gr = new GradeRow(BigDecimal.valueOf(res.score()).setScale(1, RoundingMode.HALF_UP), res.grade());
                }
                goals.add(new ProductCard.GoalGrade(g.name(), gr.score(), gr.grade().name(), calibrationService.label(g, gr.grade())));
            }
            Map<Nutrient, Double> contrib = new EnumMap<>(Nutrient.class);
            for (GradeFormula.Contribution c : GradeFormula.raw(goal, EerBand.slot(goal, band), input, cal.rules(goal)).contributions()) {
                contrib.merge(c.nutrient(), c.value(), Double::sum);
            }
            tags = NutrientEvaluator.tags(goal, NutrientEvaluator.evaluate(goal, r.basis(), r.per100g(), trans, contrib));
        }
        return new ProductCard(r.id(), r.name(), r.brandLine(), r.imageUrl(), state.name(), state.label(),
                goals, tags, sortValue(r, sort), null, saved);
    }

    /** 영양소 정렬일 때 카드 태그 — 「단백질 21g · 100g당」. */
    private static ProductCard.SortValue sortValue(Row r, ListSort sort) {
        if (sort == null || !sort.byNutrient()) return null;
        NutrientEvaluator.Fact f = sort.nutrient();
        BigDecimal v = r.per100g().get(f.graded());
        if (v == null) return null;
        String basis = r.basis();
        return new ProductCard.SortValue(f.name(), v, f.unit(), basis,
                f.label() + " " + plain(v) + f.unit() + " · " + basis + "당");
    }

    static String plain(BigDecimal v) {
        BigDecimal s = v.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }

    private Map<Long, Map<GoalType, GradeRow>> grades(Collection<Long> ids, EerBand band) {
        if (ids.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        StringBuilder slotCond = new StringBuilder();
        for (GoalType g : GoalType.values()) {
            if (!slotCond.isEmpty()) slotCond.append(" OR ");
            slotCond.append("(goal = ? AND eer_band = ?)");
            args.add(g.name());
            args.add(EerBand.slot(g, band));
        }
        args.addAll(ids);
        Map<Long, Map<GoalType, GradeRow>> out = new HashMap<>();
        jdbcTemplate.query("SELECT product_id, goal, score, grade FROM product_grades WHERE (" + slotCond + ") AND product_id IN (" + in + ")",
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new EnumMap<>(GoalType.class))
                            .put(GoalType.valueOf(rs.getString(2)), new GradeRow(rs.getBigDecimal(3), Grade.valueOf(rs.getString(4).trim())));
                }, args.toArray());
        return out;
    }

    private Set<Long> savedIds(Owner owner) {
        if (owner == null) return Set.of();
        return owner.isUser()
                ? savedProductRepository.findSavedProductIdsByUserId(owner.userId())
                : savedProductRepository.findSavedProductIdsByAnonymousId(owner.anonymousId());
    }

    private static double transThreshold(Calibration cal) {
        return cal.rules(GoalType.DEFAULT).stream()
                .filter(r -> r.nutrient() == Nutrient.TRANS_FAT)
                .mapToDouble(Calibration.Rule::threshold).findFirst().orElse(0.0);
    }

    private static GradeFormula.Input input(Map<Nutrient, BigDecimal> m) {
        return new GradeFormula.Input(d(m, Nutrient.CALORIES), d(m, Nutrient.PROTEIN), d(m, Nutrient.DIETARY_FIBER), d(m, Nutrient.SUGAR),
                d(m, Nutrient.SATURATED_FAT), d(m, Nutrient.TRANS_FAT), d(m, Nutrient.CHOLESTEROL), d(m, Nutrient.SODIUM));
    }

    private static double d(Map<Nutrient, BigDecimal> m, Nutrient n) {
        BigDecimal v = m.get(n);
        return v == null ? 0.0 : v.doubleValue();
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        Map<Nutrient, BigDecimal> per100g = new EnumMap<>(Nutrient.class);
        put(per100g, Nutrient.CALORIES, rs.getBigDecimal("calories_per_100g"));
        put(per100g, Nutrient.PROTEIN, rs.getBigDecimal("protein_per_100g"));
        put(per100g, Nutrient.DIETARY_FIBER, rs.getBigDecimal("dietary_fiber_per_100g"));
        put(per100g, Nutrient.SUGAR, rs.getBigDecimal("sugar_per_100g"));
        put(per100g, Nutrient.SATURATED_FAT, rs.getBigDecimal("saturated_fat_per_100g"));
        put(per100g, Nutrient.TRANS_FAT, rs.getBigDecimal("trans_fat_per_100g"));
        put(per100g, Nutrient.CHOLESTEROL, rs.getBigDecimal("cholesterol_per_100g"));
        put(per100g, Nutrient.SODIUM, rs.getBigDecimal("sodium_per_100g"));
        Timestamp synced = rs.getTimestamp("last_synced_at");
        Long cat = rs.getObject("category_id") == null ? null : rs.getLong("category_id");
        return new Row(rs.getLong("id"), rs.getString("name"), rs.getString("image_url"), rs.getString("brand_line"),
                rs.getBoolean("is_discontinued"), rs.getString("link_status"), rs.getString("affiliate_url"),
                synced == null ? null : synced.toLocalDateTime(), cat,
                rs.getString("serving_size"), per100g);
    }

    private static void put(Map<Nutrient, BigDecimal> m, Nutrient n, BigDecimal v) {
        if (v != null) m.put(n, v);
    }
}
