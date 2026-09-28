package com.example.nutriuniv.domain.onboarding.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 샘플 픽커 후보 조회 (기능명세서 2.2) — 분석 완료 제품만(일반 기준 등급 슬롯 존재), 등급은 일반(GENERAL) 기준.
 */
@Repository
@RequiredArgsConstructor
public class SamplePickerRepository {

    private final JdbcTemplate jdbcTemplate;

    public record Sample(long productId, String name, String imageUrl, String grade, boolean curated) {}

    private static final String GRADED_JOIN = """
            JOIN   product_grades pg ON pg.product_id = p.id AND pg.goal = 'GENERAL' AND pg.eer_band = 0
            """;

    private static RowMapper<Sample> mapper(boolean curated) {
        return (rs, i) -> new Sample(rs.getLong("id"), rs.getString("name"), rs.getString("image_url"), rs.getString("grade"), curated);
    }

    /** 관리자 지정 목록 — 활성·분석 완료만, 지정 순서. */
    public List<Sample> curated(int limit) {
        String sql = """
                SELECT p.id, p.name, p.image_url, CAST(pg.grade AS VARCHAR) AS grade
                FROM   curation_samples cs
                JOIN   products p ON p.id = cs.product_id AND p.is_active = TRUE
                """ + GRADED_JOIN + """
                WHERE  cs.is_active = TRUE
                ORDER BY cs.display_order, cs.id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, mapper(true), limit);
    }

    /** 특정 등급의 조회수 상위. */
    public List<Sample> popularByGrade(String grade, int limit) {
        String sql = """
                SELECT p.id, p.name, p.image_url, CAST(pg.grade AS VARCHAR) AS grade
                FROM   products p
                """ + GRADED_JOIN + """
                WHERE  p.is_active = TRUE AND pg.grade = ?
                ORDER BY p.view_count DESC, p.id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, mapper(false), grade, limit);
    }

    /** 등급 무관 조회수 상위. */
    public List<Sample> popular(int limit) {
        String sql = """
                SELECT p.id, p.name, p.image_url, CAST(pg.grade AS VARCHAR) AS grade
                FROM   products p
                """ + GRADED_JOIN + """
                WHERE  p.is_active = TRUE
                ORDER BY p.view_count DESC, p.id
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, mapper(false), limit);
    }
}
