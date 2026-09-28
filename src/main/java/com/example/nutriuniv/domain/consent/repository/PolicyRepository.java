package com.example.nutriuniv.domain.consent.repository;

import com.example.nutriuniv.domain.consent.entity.Policy;
import com.example.nutriuniv.domain.consent.entity.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    // 종류별 현재 버전 (bootstrap·동의 버전 검증·/meta/policies)
    Optional<Policy> findByPolicyTypeAndIsCurrentTrue(PolicyType policyType);
}
