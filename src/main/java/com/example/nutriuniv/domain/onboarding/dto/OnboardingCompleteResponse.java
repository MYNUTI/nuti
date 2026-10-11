package com.example.nutriuniv.domain.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** POST /onboarding/complete 출력. persisted=false 면 저장할 ID 가 없어(동의 전) 서버에 남기지 못했다 → 클라 localStorage 가 보조. */
@Getter
@Builder
public class OnboardingCompleteResponse {

    private boolean onboardingCompleted;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completedAt;

    private boolean persisted;
}
