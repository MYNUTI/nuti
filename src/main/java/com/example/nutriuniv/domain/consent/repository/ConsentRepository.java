package com.example.nutriuniv.domain.consent.repository;

import com.example.nutriuniv.domain.consent.entity.Consent;
import com.example.nutriuniv.domain.consent.entity.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    // 계정별 가장 최근 동의/철회 행 (현재 상태 판정·멱등 처리용). 동의는 v3 부터 계정만 한다
    Optional<Consent> findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(Long userId, ConsentType consentType);
}
