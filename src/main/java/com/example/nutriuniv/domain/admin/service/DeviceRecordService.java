package com.example.nutriuniv.domain.admin.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.admin.dto.DeviceDeletionResponse;
import com.example.nutriuniv.domain.admin.entity.DeviceDeletionLog;
import com.example.nutriuniv.domain.admin.repository.DeviceDeletionLogRepository;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 기기 기록 삭제 (v3 ADM_006) — 기기 ID 로 쌓인 개인 기록을 지운다. 한 트랜잭션.
 * <ul>
 *   <li>지운다: 최근 본 제품 · 즐겨찾기 · 기기 목표 · 기기 등록 행</li>
 *   <li>남긴다(기기 연결만 끊음): 분석 요청 · 제보(운영 대기열에서 계속 처리) · 이용 로그 · 이벤트</li>
 *   <li>삭제 이력은 device_deletion_logs 에 기기 ID 해시로 남긴다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRecordService {

    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9-]{8,36}$");

    private static final List<String> DELETE = List.of("view_history", "user_favorites", "user_goals", "anonymous_users");

    private static final List<String> UNLINK = List.of(
            "analysis_request_events", "product_reports", "app_events",
            "product_view_logs", "product_cta_logs", "product_filter_logs", "scan_event_logs", "search_logs", "visit_sessions");

    private final JdbcTemplate jdbcTemplate;
    private final DeviceDeletionLogRepository logRepository;

    @Transactional
    public DeviceDeletionResponse delete(String anonymousId, String reason, Long adminUserId) {
        String id = anonymousId == null ? "" : anonymousId.trim();
        if (!DEVICE_ID.matcher(id).matches()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "기기 ID 형식이 올바르지 않습니다.");
        }
        String memo = reason == null || reason.isBlank() ? null : reason.trim();
        if (memo != null && memo.length() > 200) memo = memo.substring(0, 200);

        Map<String, Integer> deleted = new LinkedHashMap<>();
        Map<String, Integer> unlinked = new LinkedHashMap<>();
        for (String t : DELETE) {
            if (hasTable(t)) deleted.put(t, jdbcTemplate.update("DELETE FROM " + t + " WHERE anonymous_id = ?", id));
        }
        for (String t : UNLINK) {
            if (hasTable(t)) unlinked.put(t, jdbcTemplate.update("UPDATE " + t + " SET anonymous_id = NULL WHERE anonymous_id = ?", id));
        }
        int deletedTotal = deleted.values().stream().mapToInt(Integer::intValue).sum();
        int unlinkedTotal = unlinked.values().stream().mapToInt(Integer::intValue).sum();

        logRepository.save(DeviceDeletionLog.of(ConsentService.sha256(id), adminUserId, deletedTotal, unlinkedTotal, memo));
        log.info("[ADMIN] 기기 기록 삭제 admin={} deleted={} unlinked={}", adminUserId, deletedTotal, unlinkedTotal);
        return new DeviceDeletionResponse(deleted, unlinked, deletedTotal, unlinkedTotal);
    }

    private boolean hasTable(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }
}
