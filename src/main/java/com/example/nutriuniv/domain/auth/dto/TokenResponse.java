package com.example.nutriuniv.domain.auth.dto;

import com.example.nutriuniv.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * POST /auth/oauth · POST /auth/signup · POST /auth/refresh 출력.
 * <ul>
 *   <li>가입한 계정: {accessToken, refreshToken, newUser=false, mergedRecordCount, merged, user}</li>
 *   <li>처음 로그인한 계정(v3 ACCT_004 가입 2단계): {signupRequired=true, signupToken, signupProfile} — 토큰 없음.
 *       가입 동의 뒤 POST /auth/signup 에 signupToken 을 보내면 계정을 만든다</li>
 *   <li>재발급: 토큰 둘만</li>
 * </ul>
 */
@Getter
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;

    private boolean newUser;                    // 이번 요청으로 가입됨 (POST /auth/signup)

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean signupRequired;             // 처음 로그인 — 가입 동의 화면(5-6)으로

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String signupToken;                 // 30분. 회사 계정 정보는 가입을 마칠 때까지 서버에 저장하지 않는다

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SignupProfile signupProfile;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Merged merged;                      // 종류별 옮긴 개수 (ACCT_005)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer mergedRecordCount;          // 헤더 기기 ID 에서 계정으로 옮긴 기록 합계. 옮길 것이 없으면 0

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UserSummary user;

    /** 가입 동의 화면에 보여 줄 회사 계정 정보. */
    public record SignupProfile(String provider, String email, String nickname) {}

    public record Merged(int savedProducts, int goals, int history, int analysisRequests, int reports) {}

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
