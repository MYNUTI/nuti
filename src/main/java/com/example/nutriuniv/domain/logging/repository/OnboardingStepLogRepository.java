package com.example.nutriuniv.domain.logging.repository;

import com.example.nutriuniv.domain.logging.entity.OnboardingStepLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingStepLogRepository extends JpaRepository<OnboardingStepLog, Long> {
}
