package com.example.nutriuniv.domain.analysis.repository;

import com.example.nutriuniv.domain.analysis.entity.AnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long>, JpaSpecificationExecutor<AnalysisRequest> {

    Optional<AnalysisRequest> findByBarcode(String barcode);

    Optional<AnalysisRequest> findByKeyword(String keyword);

    Optional<AnalysisRequest> findByProductId(Long productId);
}
