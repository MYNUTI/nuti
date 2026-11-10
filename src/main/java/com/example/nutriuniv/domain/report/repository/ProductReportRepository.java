package com.example.nutriuniv.domain.report.repository;

import com.example.nutriuniv.domain.report.entity.ProductReport;
import com.example.nutriuniv.domain.report.entity.ReportStatus;
import com.example.nutriuniv.domain.report.entity.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductReportRepository extends JpaRepository<ProductReport, Long> {

    Page<ProductReport> findByStatus(ReportStatus status, Pageable pageable);

    // 하루 10건 한도 — 소유자 → 세션 순
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, LocalDateTime from, LocalDateTime to);

    long countByAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String anonymousId, LocalDateTime from, LocalDateTime to);

    long countBySessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String sessionId, LocalDateTime from, LocalDateTime to);

    // 같은 사람 · 같은 제품 · 같은 항목이 아직 확인 중이면 새로 만들지 않는다 (CTRB_008)
    boolean existsByUserIdAndProductIdAndReportTypeAndStatus(Long userId, Long productId, ReportType type, ReportStatus status);

    boolean existsByAnonymousIdAndProductIdAndReportTypeAndStatus(String anonymousId, Long productId, ReportType type, ReportStatus status);

    boolean existsBySessionIdAndProductIdAndReportTypeAndStatus(String sessionId, Long productId, ReportType type, ReportStatus status);

    // 내 요청 · 제보 (CTRB_006)
    List<ProductReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ProductReport> findByAnonymousIdOrderByCreatedAtDesc(String anonymousId);

    /** 로그인 병합 (ACCT_005). */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductReport r SET r.userId = :userId, r.anonymousId = null WHERE r.anonymousId = :anonymousId")
    int transferToUser(@Param("anonymousId") String anonymousId, @Param("userId") Long userId);

    /** 계정 삭제 (ACCT_008) — 제보는 남기고 계정 연결만 끊는다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductReport r SET r.userId = null WHERE r.userId = :userId")
    int unlinkUser(@Param("userId") Long userId);

    /** 기기 기록 삭제 (ADM_006) — 제보는 남기고 기기 연결만 끊는다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductReport r SET r.anonymousId = null WHERE r.anonymousId = :anonymousId")
    int unlinkAnonymous(@Param("anonymousId") String anonymousId);
}
