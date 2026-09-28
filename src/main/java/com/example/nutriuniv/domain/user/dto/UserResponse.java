package com.example.nutriuniv.domain.user.dto;

import com.example.nutriuniv.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** GET /users/me 출력 (API 명세 「기존 수정」) — name·gender·birthDate 미수집으로 축소. role 은 관리자 화면 분기용으로 유지. */
@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String provider;   // KAKAO | GOOGLE | NAVER
    private String role;       // USER / ADMIN — 프론트 관리자 페이지 분기용

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getOauthProvider() == null ? null : user.getOauthProvider().toUpperCase())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
