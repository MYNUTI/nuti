package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.grade.entity.GradeCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeCriterionRepository extends JpaRepository<GradeCriterion, Long> {
    List<GradeCriterion> findByRecalibrationIdAndIsActiveTrue(Long recalibrationId);
}
