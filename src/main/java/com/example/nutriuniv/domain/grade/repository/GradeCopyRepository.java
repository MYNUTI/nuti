package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.grade.entity.GradeCopy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeCopyRepository extends JpaRepository<GradeCopy, Long> {
    List<GradeCopy> findByCopyCodeOrderByDisplayOrderAsc(String copyCode);
    List<GradeCopy> findAllByOrderByCopyCodeAscDisplayOrderAsc();
}
