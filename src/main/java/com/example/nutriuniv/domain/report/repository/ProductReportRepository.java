package com.example.nutriuniv.domain.report.repository;

import com.example.nutriuniv.domain.report.entity.ProductReport;
import com.example.nutriuniv.domain.report.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ProductReportRepository extends JpaRepository<ProductReport, Long> {

    Page<ProductReport> findByStatus(ReportStatus status, Pageable pageable);

    // 하루 10건 한도 — 소유자 → 세션 순
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, LocalDateTime from, LocalDateTime to);

    long countByAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String anonymousId, LocalDateTime from, LocalDateTime to);

    long countBySessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String sessionId, LocalDateTime from, LocalDateTime to);
}
