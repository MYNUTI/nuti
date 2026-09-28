package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.grade.entity.GradeAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeAnchorRepository extends JpaRepository<GradeAnchor, Long> {
    List<GradeAnchor> findByRecalibrationId(Long recalibrationId);
}
