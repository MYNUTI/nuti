package com.example.nutriuniv.domain.me.repository;

import com.example.nutriuniv.domain.me.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {

    Optional<UserSetting> findByUserId(Long userId);

    Optional<UserSetting> findByAnonymousId(String anonymousId);
}
