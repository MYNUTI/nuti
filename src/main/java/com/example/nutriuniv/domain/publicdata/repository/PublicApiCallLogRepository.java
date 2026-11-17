package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 공공 API 호출 기록(public_api_call_logs) — 한도 계산용. */
@Repository
@RequiredArgsConstructor
public class PublicApiCallLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public void log(PublicApi api, String code, LocalDateTime at) {
        jdbcTemplate.update("INSERT INTO public_api_call_logs (api, called_at, code) VALUES (?, ?, ?)",
                api.name(), Timestamp.valueOf(at), code == null ? null : code.length() > 30 ? code.substring(0, 30) : code);
    }

    /** [from, to) 사이 호출 수. */
    public int count(PublicApi api, LocalDateTime from, LocalDateTime to) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_api_call_logs WHERE api = ? AND called_at >= ? AND called_at < ?",
                Integer.class, api.name(), Timestamp.valueOf(from), Timestamp.valueOf(to));
        return n == null ? 0 : n;
    }

    /** 오래된 기록 지우기 — 지운 수. */
    public int deleteBefore(LocalDateTime before) {
        return jdbcTemplate.update("DELETE FROM public_api_call_logs WHERE called_at < ?", Timestamp.valueOf(before));
    }
}