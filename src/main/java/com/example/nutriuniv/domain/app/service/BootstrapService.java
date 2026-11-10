package com.example.nutriuniv.domain.app.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.app.dto.BootstrapResponse;
import com.example.nutriuniv.domain.body.service.BodyProfileService;
import com.example.nutriuniv.domain.consent.entity.ConsentItemCode;
import com.example.nutriuniv.domain.consent.service.ConsentReader;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 진입 초기화 (기능명세서 1.1).
 * 익명 ID 를 발급하지 않는다 — v3 부터 기기가 만든다. 첫 실행 여부(소개·목표 선택을 띄울지)는 기기가 기억한다(ONB_001).
 * 동의는 계정만 하므로 기기 ID 사용자는 동의 값이 모두 false.
 */
@Service
@RequiredArgsConstructor
public class BootstrapService {

    private final OwnerResolver ownerResolver;
    private final ConsentService consentService;
    private final ConsentReader consentReader;
    private final BodyProfileService bodyProfileService;
    private final GoalService goalService;

    @Transactional(readOnly = true)
    public BootstrapResponse bootstrap(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        EerBand band = actor.isLoggedIn() ? bodyProfileService.storedBand(actor.userId()) : null;

        return BootstrapResponse.builder()
                .loggedIn(actor.isLoggedIn())
                .goalAccountConsented(consentReader.isAgreed(owner, ConsentItemCode.GOAL_ACCOUNT))
                .bodyProfileConsented(consentReader.isAgreed(owner, ConsentItemCode.BODY_PROFILE))
                .marketingConsented(consentReader.isAgreed(owner, ConsentItemCode.MARKETING))
                .kcalBand(band == null ? null : band.kcal())
                .goal(goalService.currentGoal(owner).name())
                .currentPolicyVersion(consentService.currentPolicyVersion())
                .build();
    }
}
