package com.example.nutriuniv.domain.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /app/bootstrap 출력 (기능명세서 1.1). 이 한 번으로 첫 화면을 정한다.
 * 첫 실행 분기는 기기가 한다(ONB_001). consentAgreed·healthConsented 는 로그인한 계정의 동의 상태(기기 ID 사용자는 false).
 */
@Getter
@Builder
public class BootstrapResponse {

    @JsonProperty("isLoggedIn")            // Lombok boolean getter(isLoggedIn) 를 Jackson 이 'loggedIn' 으로 줄이는 것 방지
    private boolean loggedIn;

    private boolean consentAgreed;
    private boolean healthConsented;
    private String goal;                    // GENERAL | WEIGHT_LOSS | MUSCLE_GAIN (미설정·기기 ID 없음은 GENERAL)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currentPolicyVersion;    // 정책 행이 아직 없으면 null
}
