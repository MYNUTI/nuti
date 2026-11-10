package com.example.nutriuniv.domain.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /app/bootstrap 출력 (기능명세서 1.1). 이 한 번으로 첫 화면을 정한다.
 * 첫 실행 분기는 기기가 한다(ONB_001). 동의 값은 로그인한 계정의 선택 동의 상태(기기 ID 사용자는 false).
 * kcalBand: 몸 정보에 동의하고 저장한 계정이면 그 구간, 아니면 null(기기 값 또는 2000 을 쓴다).
 */
@Getter
@Builder
public class BootstrapResponse {

    @JsonProperty("isLoggedIn")            // Lombok boolean getter(isLoggedIn) 를 Jackson 이 'loggedIn' 으로 줄이는 것 방지
    private boolean loggedIn;

    private boolean goalAccountConsented;   // 「목표 계정 저장」
    private boolean bodyProfileConsented;   // 「몸 정보 수집·이용」
    private boolean marketingConsented;     // 「광고성 정보 수신」

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer kcalBand;
    private String goal;                    // WEIGHT_LOSS | MUSCLE_GAIN (미설정·기기 ID 없음은 WEIGHT_LOSS)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currentPolicyVersion;    // 정책 행이 아직 없으면 null
}
