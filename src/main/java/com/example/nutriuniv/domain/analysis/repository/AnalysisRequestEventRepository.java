package com.example.nutriuniv.domain.analysis.repository;

import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestEvent;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AnalysisRequestEventRepository extends JpaRepository<AnalysisRequestEvent, Long> {

    // ── 같은 기기는 한 번만 센다 ────────────────────────────────────────────────────
    boolean existsByRequestIdAndUserId(Long requestId, Long userId);

    boolean existsByRequestIdAndAnonymousId(Long requestId, String anonymousId);

    boolean existsByRequestIdAndSessionId(Long requestId, String sessionId);

    // ── 마이 화면 기여 집계 (/me/summary · /me/contributions) ───────────────────────
    @Query("SELECT COUNT(DISTINCT e.requestId) FROM AnalysisRequestEvent e WHERE e.userId = :userId")
    long countDistinctRequestsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT e.requestId) FROM AnalysisRequestEvent e WHERE e.anonymousId = :anonymousId")
    long countDistinctRequestsByAnonymousId(@Param("anonymousId") String anonymousId);

    @Query("SELECT COUNT(DISTINCT e.requestId) FROM AnalysisRequestEvent e, AnalysisRequest r " +
            "WHERE r.id = e.requestId AND r.status = :status AND e.userId = :userId")
    long countDistinctRequestsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") AnalysisRequestStatus status);

    @Query("SELECT COUNT(DISTINCT e.requestId) FROM AnalysisRequestEvent e, AnalysisRequest r " +
            "WHERE r.id = e.requestId AND r.status = :status AND e.anonymousId = :anonymousId")
    long countDistinctRequestsByAnonymousIdAndStatus(@Param("anonymousId") String anonymousId, @Param("status") AnalysisRequestStatus status);

    /** 내 요청 — [requestId, 내가 처음 요청한 시각] 최근순. 사용자가 직접 보낸 요청만(자동 등록 제외). */
    @Query("SELECT e.requestId, MIN(e.createdAt) FROM AnalysisRequestEvent e " +
            "WHERE e.userId = :userId AND e.channel IN :channels GROUP BY e.requestId ORDER BY MIN(e.createdAt) DESC")
    List<Object[]> findMyRequestsByUserId(@Param("userId") Long userId, @Param("channels") Collection<AnalysisRequestChannel> channels);

    @Query("SELECT e.requestId, MIN(e.createdAt) FROM AnalysisRequestEvent e " +
            "WHERE e.anonymousId = :anonymousId AND e.channel IN :channels GROUP BY e.requestId ORDER BY MIN(e.createdAt) DESC")
    List<Object[]> findMyRequestsByAnonymousId(@Param("anonymousId") String anonymousId, @Param("channels") Collection<AnalysisRequestChannel> channels);

    /** 로그인 병합 — 익명 소유 접수 기록을 계정으로. 옮긴 행 수를 돌려준다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AnalysisRequestEvent e SET e.userId = :userId, e.anonymousId = null WHERE e.anonymousId = :anonymousId")
    int transferToUser(@Param("anonymousId") String anonymousId, @Param("userId") Long userId);

    /** 기기 기록 삭제(ADM_006) — 요청은 남기고 기기 연결만 끊는다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AnalysisRequestEvent e SET e.anonymousId = null WHERE e.anonymousId = :anonymousId")
    int unlinkAnonymous(@Param("anonymousId") String anonymousId);

    // ── 일일 한도 ──────────────────────────────────────────────────────────────────
    long countByChannelInAndUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Collection<AnalysisRequestChannel> channels, Long userId, LocalDateTime from, LocalDateTime to);

    long countByChannelInAndAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Collection<AnalysisRequestChannel> channels, String anonymousId, LocalDateTime from, LocalDateTime to);

    long countByChannelInAndSessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Collection<AnalysisRequestChannel> channels, String sessionId, LocalDateTime from, LocalDateTime to);
}
