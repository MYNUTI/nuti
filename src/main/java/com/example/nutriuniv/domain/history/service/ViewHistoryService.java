package com.example.nutriuniv.domain.history.service;

import com.example.nutriuniv.domain.body.service.KcalBandResolver;
import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.history.dto.HistoryResponse;
import com.example.nutriuniv.domain.history.entity.ViewHistory;
import com.example.nutriuniv.domain.history.entity.ViewSource;
import com.example.nutriuniv.domain.history.repository.ViewHistoryRepository;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.service.ProductCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 최근 본 제품 (v3 RTN_001).
 * <ul>
 *   <li>결과 화면을 열 때마다 한 줄 기록(소유자 · 제품 · 시각 · 경로). 소유자가 없으면 남기지 않는다</li>
 *   <li>목록은 제품별로 묶어 마지막으로 본 순. 등급 필터는 요청 목표 기준(A~E, 점수 없음 = NONE)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewHistoryService {

    public static final int MAX_PAGE_SIZE = 50;
    public static final List<String> GRADES = List.of("A", "B", "C", "D", "E", "NONE");

    private final KcalBandResolver kcalBandResolver;
    private final ViewHistoryRepository repository;
    private final OwnerResolver ownerResolver;
    private final ProductCardService cardService;
    private final JdbcTemplate jdbcTemplate;

    /** 기록 실패가 결과 화면을 막지 않게 따로 커밋한다. 소유자가 없으면 남기지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Owner owner, Long productId, ViewSource source) {
        if (owner == null || productId == null) return;
        repository.save(ViewHistory.of(owner, productId, source == null ? ViewSource.OTHER : source, LocalDateTime.now()));
    }

    // ── GET /me/history ───────────────────────────────────────────────────────────

    private record Viewed(long productId, LocalDateTime viewedAt, String source, long count) {}

    @Transactional(readOnly = true)
    public HistoryResponse list(Actor actor, String goalParam, Integer kcalBand, List<String> gradeParam, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        Owner owner = ownerResolver.resolve(actor);
        GoalType goal = goalParam == null || goalParam.isBlank() ? GoalType.DEFAULT : GoalType.from(goalParam);
        EerBand band = kcalBandResolver.resolve(actor, kcalBand);
        List<String> grades = grades(gradeParam);

        List<Object> args = new ArrayList<>(List.of(goal.name(), EerBand.slot(goal, band)));
        String where = ownerWhere(owner, args) + gradeWhere(grades, args);
        String from = " FROM view_history h"
                + " JOIN products p ON p.id = h.product_id AND p.is_active = TRUE"
                + " LEFT JOIN product_grades pg ON pg.product_id = h.product_id AND pg.goal = ? AND pg.eer_band = ?"
                + " WHERE " + where;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT h.product_id)" + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Viewed> viewed = jdbcTemplate.query(
                "SELECT h.product_id, MAX(h.viewed_at) AS last_at,"
                        + " (ARRAY_AGG(h.source ORDER BY h.viewed_at DESC))[1] AS last_source, COUNT(*) AS n"
                        + from + " GROUP BY h.product_id ORDER BY last_at DESC, h.product_id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Timestamp t = rs.getTimestamp(2);
                    return new Viewed(rs.getLong(1), t == null ? null : t.toLocalDateTime(), rs.getString(3), rs.getLong(4));
                }, pageArgs.toArray());

        List<ProductCard> cards = cardService.withBetter(
                cardService.cards(viewed.stream().map(Viewed::productId).toList(), goal, band, owner, null), goal, band);
        Map<Long, ProductCard> byId = new HashMap<>();
        cards.forEach(c -> byId.put(c.productId(), c));
        List<HistoryResponse.Item> items = new ArrayList<>();
        for (Viewed v : viewed) {
            ProductCard c = byId.get(v.productId());
            if (c != null) items.add(new HistoryResponse.Item(c, v.viewedAt(), v.source(), v.count()));
        }
        long t = total == null ? 0 : total;
        return new HistoryResponse(goal.name(), band.kcal(), grades, t, page, size, (long) (page + 1) * size < t, items);
    }

    // ── 홈 집계 (SRCH_006) ────────────────────────────────────────────────────────

    /** 본 제품 수(제품 기준)와 목표별 등급 분포(A~E · NONE). 소유자가 없으면 0. */
    public record ViewedStats(long viewedCount, Map<GoalType, Map<String, Long>> distribution) {}

    @Transactional(readOnly = true)
    public ViewedStats stats(Owner owner, EerBand band) {
        Map<GoalType, Map<String, Long>> dist = new EnumMap<>(GoalType.class);
        for (GoalType g : GoalType.values()) {
            Map<String, Long> m = new LinkedHashMap<>();
            for (String s : GRADES) m.put(s, 0L);
            dist.put(g, m);
        }
        if (owner == null) return new ViewedStats(0, dist);

        List<Object> args = new ArrayList<>();
        String viewed = "SELECT DISTINCT h.product_id FROM view_history h JOIN products p ON p.id = h.product_id AND p.is_active = TRUE"
                + " WHERE " + ownerWhere(owner, args);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + viewed + ") v", Long.class, args.toArray());
        long n = count == null ? 0 : count;
        if (n == 0) return new ViewedStats(0, dist);

        List<Object> gArgs = new ArrayList<>();
        StringBuilder slots = new StringBuilder();
        for (GoalType g : GoalType.values()) {
            if (!slots.isEmpty()) slots.append(" OR ");
            slots.append("(pg.goal = ? AND pg.eer_band = ?)");
            gArgs.add(g.name());
            gArgs.add(EerBand.slot(g, band));
        }
        gArgs.addAll(args);
        jdbcTemplate.query("SELECT pg.goal, TRIM(pg.grade), COUNT(*) FROM product_grades pg"
                        + " JOIN products p ON p.id = pg.product_id AND p.status = 'ANALYZED'"
                        + " WHERE (" + slots + ") AND pg.product_id IN (" + viewed + ") GROUP BY 1, 2",
                rs -> {
                    dist.get(GoalType.valueOf(rs.getString(1))).merge(rs.getString(2), rs.getLong(3), Long::sum);
                }, gArgs.toArray());
        Long none = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products p WHERE p.status <> 'ANALYZED' AND p.id IN (" + viewed + ")",
                Long.class, args.toArray());
        for (Map<String, Long> m : dist.values()) m.put("NONE", none == null ? 0 : none);
        return new ViewedStats(n, dist);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private static String ownerWhere(Owner owner, List<Object> args) {
        if (owner.isUser()) {
            args.add(owner.userId());
            return "h.user_id = ?";
        }
        args.add(owner.anonymousId());
        return "h.anonymous_id = ?";
    }

    private static String gradeWhere(List<String> grades, List<Object> args) {
        if (grades.isEmpty()) return "";
        List<String> letters = grades.stream().filter(g -> !"NONE".equals(g)).toList();
        List<String> parts = new ArrayList<>();
        if (!letters.isEmpty()) {
            parts.add("(p.status = 'ANALYZED' AND TRIM(pg.grade) IN (" + String.join(",", Collections.nCopies(letters.size(), "?")) + "))");
            args.addAll(letters);
        }
        if (grades.contains("NONE")) parts.add("p.status <> 'ANALYZED'");
        return " AND (" + String.join(" OR ", parts) + ")";
    }

    private static List<String> grades(List<String> raw) {
        if (raw == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            if (r == null || r.isBlank()) continue;
            String g = r.trim().toUpperCase();
            if (!GRADES.contains(g)) {
                throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "grade는 A~E 또는 NONE 이어야 합니다.");
            }
            if (!out.contains(g)) out.add(g);
        }
        return out;
    }
}
