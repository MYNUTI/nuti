package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.grade.entity.GradeRecalibration;
import com.example.nutriuniv.domain.grade.entity.RecalibrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeRecalibrationRepository extends JpaRepository<GradeRecalibration, Long> {

    /** 현행 기준 = APPLIED 중 가장 최근 적용. */
    Optional<GradeRecalibration> findTopByStatusOrderByAppliedAtDesc(RecalibrationStatus status);

    List<GradeRecalibration> findAllByOrderByCreatedAtDesc();

    boolean existsByVersion(String version);
}
