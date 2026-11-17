package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.compute.BrandGrouping;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 묶음 구성표(brand_groups) — JDBC. */
@Repository
@RequiredArgsConstructor
public class BrandGroupRepository {

    private static final String INSERT_SQL = """
            INSERT INTO brand_groups (food_cd, group_key, is_representative, created_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (food_cd) DO NOTHING
            """;
    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public Map<String, BrandGrouping.Stored> findAll() {
        Map<String, BrandGrouping.Stored> m = new HashMap<>();
        jdbcTemplate.query("SELECT food_cd, group_key, is_representative FROM brand_groups",
                rs -> { m.put(rs.getString(1), new BrandGrouping.Stored(rs.getString(2), rs.getBoolean(3))); });
        return m;
    }

    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand_groups", Long.class);
        return n == null ? 0 : n;
    }

    /** 더한다 — 이미 있는 food_cd 는 건너뛴다(한 번 정한 묶음은 바꾸지 않는다). 호출자가 트랜잭션을 연다. */
    public void insertAll(List<BrandGrouping.Assigned> rows, LocalDateTime at) {
        Timestamp ts = Timestamp.valueOf(at);
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = new ArrayList<>();
            for (BrandGrouping.Assigned a : rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE))) {
                batch.add(new Object[]{a.foodCd(), a.groupKey(), a.representative(), ts});
            }
            jdbcTemplate.batchUpdate(INSERT_SQL, batch);
        }
    }

    /** 통째로 바꾼다(통합 마스터의 묶음을 올릴 때). 호출자가 트랜잭션을 연다. */
    public void replaceAll(List<BrandGrouping.Assigned> rows, LocalDateTime at) {
        jdbcTemplate.update("DELETE FROM brand_groups");
        insertAll(rows, at);
    }
}