package com.example.nutriuniv.domain.me.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.me.entity.UserSetting;
import com.example.nutriuniv.domain.me.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 설정 조회·저장 — 소유자 없음(동의 전)·행 없음은 기본값(clipboardLinkDetection=true). */
@Service
@RequiredArgsConstructor
public class UserSettingService {

    private final UserSettingRepository repository;

    @Transactional(readOnly = true)
    public boolean clipboardLinkDetection(Owner owner) {
        return find(owner).map(UserSetting::isClipboardLinkDetection).orElse(true);
    }

    @Transactional
    public UserSetting changeClipboardLinkDetection(Owner owner, boolean on) {
        UserSetting s = find(owner).orElseGet(() -> repository.save(UserSetting.create(owner)));
        s.changeClipboardLinkDetection(on);
        return s;
    }

    private Optional<UserSetting> find(Owner owner) {
        if (owner == null) return Optional.empty();
        return owner.isUser() ? repository.findByUserId(owner.userId()) : repository.findByAnonymousId(owner.anonymousId());
    }
}
