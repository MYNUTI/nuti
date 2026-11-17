package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.dto.NoCategoryPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 검토 목록 — 공공데이터 최신화가 채우지 못한 값. 분류(category)는 LLM 이 의미를 읽고 채운 값이라 규칙으로 만들 수 없어,
 * 새로 들어온 제품은 비어 있다. 그것을 최신순으로 보여 준다.
 */
@Repository
@RequiredArgsConstructor
public class SyncReviewRepository {

    private static final String WHERE = """
             FROM products p
            WHERE p.category_id IS NULL AND p.is_active = TRUE AND p.source IN ('nutri_db', 'foodqr')
            """;

    private final JdbcTemplate jdbcTemplate;

    public NoCategoryPageResponse findNoCategory(int page, int size) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + WHERE, Long.class);
        List<NoCategoryPageResponse.Item> items = jdbcTemplate.query(
                "SELECT p.id, p.master_id, p.source, p.name, p.maker, p.created_at" + WHERE
                        + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp(6);
                    return new NoCategoryPageResponse.Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), ts == null ? null : ts.toLocalDateTime());
                },
                size, (long) page * size);
        return new NoCategoryPageResponse(total == null ? 0 : total, page, size, items);
    }
}