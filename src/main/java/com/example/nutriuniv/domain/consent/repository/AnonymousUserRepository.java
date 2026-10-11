package com.example.nutriuniv.domain.consent.repository;

import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, Long> {

    // 헤더 X-Anonymous-Id → 발급된 익명 사용자 (없으면 서버가 발급한 적 없는 값)
    Optional<AnonymousUser> findByAnonymousId(String anonymousId);
}
