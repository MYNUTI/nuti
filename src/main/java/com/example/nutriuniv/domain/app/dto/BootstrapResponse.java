package com.example.nutriuniv.domain.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /app/bootstrap 출력 (기능명세서 1.1). 이 한 번으로 첫 화면을 정한다.
 * 라우팅 분기는 onboardingCompleted 만 — consentAgreed·healthConsented 는 찍기·저장·목표 시도 순간의 판단용.
 */
@Getter
@Builder
public class BootstrapResponse {

    @JsonProperty("isLoggedIn")            // Lombok boolean getter(isLoggedIn) 를 Jackson 이 'loggedIn' 으로 줄이는 것 방지
    private boolean loggedIn;

    private boolean consentAgreed;
    private boolean healthConsented;
    private boolean onboardingCompleted;
    private String goal;                    // GENERAL | WEIGHT_LOSS | MUSCLE_GAIN (미설정·동의 전은 GENERAL)
    private boolean clipboardLinkDetection;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currentPolicyVersion;    // 정책 행이 아직 없으면 null
}
