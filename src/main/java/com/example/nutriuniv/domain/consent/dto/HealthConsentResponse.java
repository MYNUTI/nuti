package com.example.nutriuniv.domain.consent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** /me/health-consent 3종 공통 출력. 「동의 안 함」은 오류가 아니라 healthConsented=false 인 정상 응답. */
@Getter
@Builder
public class HealthConsentResponse {

    private boolean healthConsented;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime agreedAt;

    public static HealthConsentResponse agreed(LocalDateTime agreedAt) {
        return HealthConsentResponse.builder().healthConsented(true).agreedAt(agreedAt).build();
    }

    public static HealthConsentResponse notAgreed() {
        return HealthConsentResponse.builder().healthConsented(false).build();
    }
}
