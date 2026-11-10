package com.example.nutriuniv.domain.user.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 계정 삭제 (v3 ACCT_008) — 되돌릴 수 없다. 한 트랜잭션: 실패하면 계정을 그대로 둔다.
 * <ul>
 *   <li>지운다: 계정 정보 · 계정 목표 · 즐겨찾기 · 최근 본 제품 · 동의 이력 · 몸 정보(칼로리 구간 포함) · 로그인 토큰 · 추천 캐시 · 기기 연결</li>
 *   <li>남긴다(계정 연결만 끊음): 분석 요청 · 제보 — 운영 대기열에서 계속 처리한다. 이용 로그 · 이벤트도 계정 칸만 비운다</li>
 *   <li>같은 회사 계정으로 다시 로그인하면 새 가입으로 처리된다(행을 지우므로)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    /** 계정 소유 행을 지우는 표 (열 이름). */
    private static final List<String[]> DELETE = List.of(
            new String[]{"auth_token", "user_id"},
            new String[]{"user_goals", "user_id"},
            new String[]{"user_favorites", "user_id"},
            new String[]{"view_history", "user_id"},
            new String[]{"consent_records", "user_id"},
            new String[]{"consents", "user_id"},            // v2 동의 원장 (엔티티는 지웠고 표가 남아 있을 수 있다)
            new String[]{"body_profiles", "user_id"},
            new String[]{"user_nutrition", "user_id"},      // v2 영양정보
            new String[]{"recommendation_cache", "user_id"},
            new String[]{"anonymous_users", "merged_user_id"}
    );

    /** 행은 남기고 계정 칸만 비우는 표. */
    private static final List<String> UNLINK = List.of(
            "analysis_request_events", "product_reports",
            "product_view_logs", "product_cta_logs", "product_filter_logs", "scan_event_logs", "search_logs", "visit_sessions",
            "app_events");

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void delete(Long userId) {
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        if (exists == null || exists == 0) throw new CustomException(ErrorCode.USER_NOT_FOUND);

        for (String[] t : DELETE) {
            if (hasTable(t[0])) jdbcTemplate.update("DELETE FROM " + t[0] + " WHERE " + t[1] + " = ?", userId);
        }
        for (String t : UNLINK) {
            if (hasTable(t)) jdbcTemplate.update("UPDATE " + t + " SET user_id = NULL WHERE user_id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        log.info("[ACCOUNT] 계정 삭제 userId={}", userId);
    }

    private boolean hasTable(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }
}
