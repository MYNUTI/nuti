package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** 공공 API 원본(public_api_raw) — JDBC. payload 는 응답 한 행을 JSON 문자열 그대로 jsonb 로. */
@Repository
@RequiredArgsConstructor
public class PublicApiRawRepository {

    private static final String INSERT_SQL = """
            INSERT INTO public_api_raw (api, round_id, row_hash, payload, collected_at)
            VALUES (?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (api, round_id, row_hash) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 한 페이지의 행을 넣는다. 같은 회차에 이미 있는 행은 건너뛴다. 넣은 수. 호출자가 트랜잭션을 연다. */
    public int insert(PublicApi api, long roundId, List<Map<String, Object>> rows, LocalDateTime at) {
        if (rows.isEmpty()) return 0;
        Timestamp ts = Timestamp.valueOf(at);
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String json = objectMapper.writeValueAsString(row);
            batch.add(new Object[]{api.name(), roundId, sha256(json), json, ts});
        }
        int inserted = 0;
        for (int n : jdbcTemplate.batchUpdate(INSERT_SQL, batch)) inserted += Math.max(n, 0);
        return inserted;
    }

    /** 전량 회차가 끝났다 — 그 회차가 시작되기 전에 시작한 회차들의 행을 지운다(그 사이 시작한 증분 회차는 남긴다). 지운 수. */
    public int deleteRowsBefore(PublicApi api, long fullRoundId, LocalDateTime fullRoundStartedAt) {
        return jdbcTemplate.update("""
                DELETE FROM public_api_raw
                WHERE  api = ?
                  AND  round_id IN (SELECT r.id FROM public_api_rounds r
                                     WHERE r.api = ? AND r.id <> ? AND r.started_at < ?)
                """, api.name(), api.name(), fullRoundId, Timestamp.valueOf(fullRoundStartedAt));
    }

    /** 실패한 회차의 행을 지운다 — 일부만 받은 전량 회차가 재계산에 섞이지 않게. */
    public int deleteRound(long roundId) {
        return jdbcTemplate.update("DELETE FROM public_api_raw WHERE round_id = ?", roundId);
    }

    public long count(PublicApi api) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public_api_raw WHERE api = ?", Long.class, api.name());
        return n == null ? 0 : n;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}