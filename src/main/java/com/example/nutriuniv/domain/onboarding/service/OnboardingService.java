package com.example.nutriuniv.domain.onboarding.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.onboarding.dto.OnboardingCompleteRequest;
import com.example.nutriuniv.domain.onboarding.dto.OnboardingCompleteResponse;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 온보딩 완료 처리 (API 명세 /onboarding/complete). bootstrap 의 onboardingCompleted 가 여기서 기록된 시각을 본다.
 * 이미 완료면 200 멱등. 동의 없이 끝낸 사용자는 저장할 ID 가 없어 persisted=false 로 응답 — 클라 localStorage 가 보조하고,
 * 나중에 동의로 ID 가 생기면 다시 호출해 동기화한다(팀 확인 사항).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OwnerResolver ownerResolver;
    private final UserRepository userRepository;
    private final AnonymousUserRepository anonymousUserRepository;

    @Transactional
    public OnboardingCompleteResponse complete(Actor actor, OnboardingCompleteRequest request) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        if (request != null && request.getSkippedSteps() != null && !request.getSkippedSteps().isEmpty()) {
            log.debug("[ONBOARDING] 완료 — 건너뛴 단계 {}", request.getSkippedSteps());
        }
        if (owner == null) {
            return OnboardingCompleteResponse.builder()
                    .onboardingCompleted(true).completedAt(LocalDateTime.now()).persisted(false).build();
        }
        LocalDateTime completedAt;
        if (owner.isUser()) {
            User user = userRepository.findById(owner.userId()).orElse(null);
            if (user == null) {
                return OnboardingCompleteResponse.builder()
                        .onboardingCompleted(true).completedAt(LocalDateTime.now()).persisted(false).build();
            }
            user.completeOnboarding();
            completedAt = user.getOnboardingCompletedAt();
        } else {
            AnonymousUser anon = anonymousUserRepository.findByAnonymousId(owner.anonymousId()).orElseThrow();
            anon.completeOnboarding();
            completedAt = anon.getOnboardingCompletedAt();
        }
        return OnboardingCompleteResponse.builder()
                .onboardingCompleted(true).completedAt(completedAt).persisted(true).build();
    }
}
