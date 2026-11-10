package com.example.nutriuniv.domain.analysis.repository;

import com.example.nutriuniv.domain.analysis.entity.AnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long>, JpaSpecificationExecutor<AnalysisRequest> {

    Optional<AnalysisRequest> findByBarcode(String barcode);

    Optional<AnalysisRequest> findByKeyword(String keyword);

    Optional<AnalysisRequest> findByProductId(Long productId);

    /** [status, count] — 관리자 대기열 상태별 건수. */
    @Query("SELECT r.status, COUNT(r) FROM AnalysisRequest r GROUP BY r.status")
    List<Object[]> countGroupByStatus();
}
