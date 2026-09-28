package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.grade.calc.GradeFormula;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 등급 계산 입력 — 「분석 완료」 제품(판정 7종 *_per_100g 전부 있음, 기능명세서 4.1)의 100g 기준 영양성분.
 * 식이섬유는 없으면 0 + fiberIncluded=false. 대분류(parent 없으면 자기 분류)는 백분위 그룹 키.
 */
@Repository
@RequiredArgsConstructor
public class GradeInputRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String FROM_ANALYZED = """
            FROM   products p
            JOIN   categories c         ON c.id = p.category_id
            JOIN   product_nutrients pn ON pn.product_id = p.id
            WHERE  p.is_active = TRUE
              AND  pn.calories_per_100g      IS NOT NULL
              AND  pn.protein_per_100g       IS NOT NULL
              AND  pn.sugar_per_100g         IS NOT NULL
              AND  pn.saturated_fat_per_100g IS NOT NULL
              AND  pn.trans_fat_per_100g     IS NOT NULL
              AND  pn.cholesterol_per_100g   IS NOT NULL
              AND  pn.sodium_per_100g        IS NOT NULL
            """;

    public record GradeInput(long productId, long groupCategoryId, GradeFormula.Input input, boolean fiberIncluded) {}

    public List<GradeInput> findAnalyzedInputs() {
        String sql = """
                SELECT p.id,
                       COALESCE(c.parent_id, c.id) AS group_id,
                       pn.calories_per_100g, pn.protein_per_100g, pn.dietary_fiber_per_100g, pn.sugar_per_100g,
                       pn.saturated_fat_per_100g, pn.trans_fat_per_100g, pn.cholesterol_per_100g, pn.sodium_per_100g
                """ + FROM_ANALYZED + " ORDER BY p.id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> map(rs));
    }

    public long countAnalyzed() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) " + FROM_ANALYZED, Long.class);
        return n == null ? 0 : n;
    }

    private static GradeInput map(ResultSet rs) throws SQLException {
        BigDecimal fiber = rs.getBigDecimal("dietary_fiber_per_100g");
        GradeFormula.Input input = new GradeFormula.Input(
                num(rs, "calories_per_100g"),
                num(rs, "protein_per_100g"),
                fiber == null ? 0.0 : fiber.doubleValue(),
                num(rs, "sugar_per_100g"),
                num(rs, "saturated_fat_per_100g"),
                num(rs, "trans_fat_per_100g"),
                num(rs, "cholesterol_per_100g"),
                num(rs, "sodium_per_100g")
        );
        return new GradeInput(rs.getLong("id"), rs.getLong("group_id"), input, fiber != null);
    }

    private static double num(ResultSet rs, String column) throws SQLException {
        BigDecimal v = rs.getBigDecimal(column);
        return v == null ? 0.0 : v.doubleValue();
    }
}
