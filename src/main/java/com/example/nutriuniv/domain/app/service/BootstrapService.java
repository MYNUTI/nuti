package com.example.nutriuniv.domain.app.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.app.dto.BootstrapResponse;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.service.GoalService;
import com.example.nutriuniv.domain.me.service.UserSettingService;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 진입 초기화 (기능명세서 1.1).
 * 익명 ID 를 발급하지 않는다(발급은 동의 API). 동의 전 사용자는 소유자가 없으므로 전부 false/GENERAL.
 * 동의 없이 픽커만 구경하고 온보딩을 끝낸 사용자의 완료 여부는 클라이언트 localStorage 가 보조한다(팀 확인 사항).
 */
@Service
@RequiredArgsConstructor
public class BootstrapService {

    private final OwnerResolver ownerResolver;
    private final ConsentService consentService;
    private final GoalService goalService;
    private final UserRepository userRepository;
    private final AnonymousUserRepository anonymousUserRepository;
    private final UserSettingService userSettingService;

    @Transactional(readOnly = true)
    public BootstrapResponse bootstrap(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);

        return BootstrapResponse.builder()
                .loggedIn(actor.isLoggedIn())
                .consentAgreed(owner != null && consentService.isPrivacyConsented(owner))
                .healthConsented(owner != null && consentService.isHealthConsented(owner))
                .onboardingCompleted(owner != null && onboardingCompleted(owner))
                .goal(goalService.currentGoal(owner).name())
                .clipboardLinkDetection(userSettingService.clipboardLinkDetection(owner))   // user_settings, 없으면 ON
                .currentPolicyVersion(consentService.currentPolicyVersion())
                .build();
    }

    private boolean onboardingCompleted(Owner owner) {
        if (owner.isUser()) {
            return userRepository.findById(owner.userId()).map(User::getOnboardingCompletedAt).isPresent();
        }
        return anonymousUserRepository.findByAnonymousId(owner.anonymousId())
                .map(AnonymousUser::getOnboardingCompletedAt).isPresent();
    }
}
