package com.example.nutriuniv.domain.consent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /onboarding/consent · POST /me/health-consent 공통 입력. agreedItems 의 코드 목록은 팀 확정 사항. */
@Getter
@NoArgsConstructor
public class ConsentRequest {

    @NotBlank(message = "policyVersion은 필수입니다.")
    private String policyVersion;

    private List<String> agreedItems;
}
