package com.example.nutriuniv.domain.consent.repository;

import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, Long> {

    // 기기 익명 ID → 계정 연결 기록 (없으면 아직 로그인으로 옮긴 적 없는 기기)
    Optional<AnonymousUser> findByAnonymousId(String anonymousId);
}
