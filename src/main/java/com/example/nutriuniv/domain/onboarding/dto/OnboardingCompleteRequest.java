package com.example.nutriuniv.domain.onboarding.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /onboarding/complete 입력 — 건너뛴 단계 목록(참고용, 저장은 온보딩 단계 로그가 한다). */
@Getter
@NoArgsConstructor
public class OnboardingCompleteRequest {
    private List<String> skippedSteps;
}
