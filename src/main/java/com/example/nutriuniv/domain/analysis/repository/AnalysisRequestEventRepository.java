package com.example.nutriuniv.domain.analysis.repository;

import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AnalysisRequestEventRepository extends JpaRepository<AnalysisRequestEvent, Long> {

    // 마이 화면 기여 집계 (/me/summary)
    long countByUserId(Long userId);

    long countByAnonymousId(String anonymousId);

    @Query("SELECT COUNT(DISTINCT e.requestId) FROM AnalysisRequestEvent e, AnalysisRequest r " +
            "WHERE r.id = e.requestId AND r.status = :status AND e.userId = :userId")
    long countDistinctRequestsByUserIdAndStatus(@Param("userId") Long userId,
                                                @Param("status") com.example.nutriuniv.domain.analysis.entity.AnalysisRequestStatus status);

    @Query("SELECT COUNT(DISTINCT e.requestId) FROM AnalysisRequestEvent e, AnalysisRequest r " +
            "WHERE r.id = e.requestId AND r.status = :status AND e.anonymousId = :anonymousId")
    long countDistinctRequestsByAnonymousIdAndStatus(@Param("anonymousId") String anonymousId,
                                                     @Param("status") com.example.nutriuniv.domain.analysis.entity.AnalysisRequestStatus status);

    /** 로그인 병합(7.2) — 익명 소유 접수 기록을 계정으로. 옮긴 행 수를 돌려준다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AnalysisRequestEvent e SET e.userId = :userId, e.anonymousId = null WHERE e.anonymousId = :anonymousId")
    int transferToUser(@Param("anonymousId") String anonymousId, @Param("userId") Long userId);

    long countByChannelAndUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            AnalysisRequestChannel channel, Long userId, LocalDateTime from, LocalDateTime to);

    long countByChannelAndAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            AnalysisRequestChannel channel, String anonymousId, LocalDateTime from, LocalDateTime to);

    long countByChannelAndSessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            AnalysisRequestChannel channel, String sessionId, LocalDateTime from, LocalDateTime to);
}
