package com.example.nutriuniv.domain.ranking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 칩별 제품 수 · 분석 완료 수 (게이트 · 「000개 중 000개 점수 있음」). 전체를 한 번에 세고 10분 들고 있는다.
 * 칩에 보이는 제품 = 그 중분류 제품 + 「같이 보이는 곳」으로 그 칩을 단 제품.
 */
@Service
@RequiredArgsConstructor
public class ChipStatsService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbcTemplate;

    public record Stat(long total, long analyzed) {
        public static final Stat EMPTY = new Stat(0, 0);

        public boolean open() {
            return RankingGate.passes(analyzed);
        }
    }

    private record Snapshot(Map<Long, Stat> stats, LocalDateTime updatedAt, Instant loadedAt) {}

    private volatile Snapshot snapshot;

    public Stat stat(long categoryId) {
        return current().stats().getOrDefault(categoryId, Stat.EMPTY);
    }

    /** 마지막으로 끝난 제품 적재 시각 (없으면 null). */
    public LocalDateTime updatedAt() {
        return current().updatedAt();
    }

    /** 적재 · 재계산 뒤 바로 반영하고 싶을 때. */
    public void invalidate() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null || s.loadedAt().plus(TTL).isBefore(Instant.now())) {
            s = load();
            snapshot = s;
        }
        return s;
    }

    private Snapshot load() {
        Map<Long, Stat> stats = new HashMap<>();
        jdbcTemplate.query("""
                SELECT m.cid, COUNT(*) AS total, COUNT(*) FILTER (WHERE m.status = 'ANALYZED') AS analyzed
                  FROM (SELECT p.id, p.category_id AS cid, p.status
                          FROM products p WHERE p.is_active = TRUE AND p.category_id IS NOT NULL
                        UNION
                        SELECT p.id, pcc.category_id, p.status
                          FROM product_category_chips pcc JOIN products p ON p.id = pcc.product_id
                         WHERE p.is_active = TRUE) m
                 GROUP BY m.cid
                """, rs -> {
            stats.put(rs.getLong(1), new Stat(rs.getLong(2), rs.getLong(3)));
        });
        Timestamp t = jdbcTemplate.queryForObject("SELECT MAX(finished_at) FROM product_loads WHERE status = 'DONE'", Timestamp.class);
        return new Snapshot(stats, t == null ? null : t.toLocalDateTime(), Instant.now());
    }
}
