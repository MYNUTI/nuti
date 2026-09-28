package com.example.nutriuniv.domain.onboarding.repository;

import com.example.nutriuniv.domain.onboarding.entity.CurationSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurationSampleRepository extends JpaRepository<CurationSample, Long> {

    List<CurationSample> findAllByOrderByIsActiveDescDisplayOrderAscIdAsc();
}
