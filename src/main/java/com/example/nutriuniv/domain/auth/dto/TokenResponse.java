package com.example.nutriuniv.domain.auth.dto;

import com.example.nutriuniv.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * POST /auth/oauth · POST /auth/refresh 출력.
 * 로그인: {accessToken, refreshToken, newUser, mergedRecordCount, user}. 재발급: 토큰 둘만(나머지 생략).
 * 구 응답의 email·oauthId(register 호출용)는 register 폐지로 없어졌다.
 */
@Getter
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;

    private boolean newUser;                    // 이번 로그인으로 가입됨 (탈퇴 후 재가입 포함)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer mergedRecordCount;          // 헤더 익명 ID 에서 계정으로 옮긴 저장·목표·기여 건수 (7.2). 병합할 것 없으면 0

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UserSummary user;

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String email;
        private String nickname;
        private String provider;                // KAKAO | GOOGLE | NAVER

        public static UserSummary from(User user) {
            return UserSummary.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .provider(user.getOauthProvider() == null ? null : user.getOauthProvider().toUpperCase())
                    .build();
        }
    }
}
