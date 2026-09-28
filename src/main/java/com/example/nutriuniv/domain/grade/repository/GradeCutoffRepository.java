package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.grade.entity.GradeCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeCutoffRepository extends JpaRepository<GradeCutoff, Long> {
    List<GradeCutoff> findByRecalibrationIdAndIsActiveTrue(Long recalibrationId);
}
