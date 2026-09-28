package com.example.nutriuniv.domain.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/** GET·PATCH /me/settings 출력 (API 명세) — 설정 화면 1회 호출 구성. notification 은 3차 보류. */
@Getter
@Builder
public class MeSettingsResponse {

    private boolean clipboardLinkDetection;
    private Goal goal;
    private Policies policies;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String supportUrl;

    private String appVersion;              // 플랫폼 표기 포함 (예: 2.0.0-web)

    @Getter
    @Builder
    public static class Goal {
        private String code;
        private String label;
    }

    @Getter
    @Builder
    public static class Policies {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String privacyUrl;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String termsUrl;
    }
}
