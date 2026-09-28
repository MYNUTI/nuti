package com.example.nutriuniv.domain.auth.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.auth.dto.LogoutResponse;
import com.example.nutriuniv.domain.auth.dto.OAuthLoginRequest;
import com.example.nutriuniv.domain.auth.dto.RefreshRequest;
import com.example.nutriuniv.domain.auth.dto.TokenResponse;
import com.example.nutriuniv.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // 소셜 콘솔에 등록된 redirect-uri 와 1:1 — 경로 변경 불가. 토큰 교환은 하지 않고 code 만 프론트로 전달.

    @Operation(summary = "구글 OAuth 콜백")
    @GetMapping("/oauth/google")
    public ResponseEntity<Void> googleCallback(@RequestParam String code) {
        String redirectUrl = frontendUrl + "?code=" + code + "&provider=GOOGLE";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    @Operation(summary = "카카오 OAuth 콜백")
    @GetMapping("/oauth/kakao")
    public ResponseEntity<Void> kakaoCallback(@RequestParam String code) {
        String redirectUrl = frontendUrl + "?code=" + code + "&provider=KAKAO";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    @Operation(summary = "네이버 OAuth 콜백")
    @GetMapping("/oauth/naver")
    public ResponseEntity<Void> naverCallback(@RequestParam String code) {
        String redirectUrl = frontendUrl + "?code=" + code + "&provider=NAVER";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    // POST /auth/oauth
    @Operation(summary = "소셜 로그인 (+7.2 병합 내장)",
            description = "기존회원·신규회원 모두 즉시 토큰을 발급합니다(등록 단계 폐지, 이메일 외 프로필 미수집). newUser 로 신규 여부 구분. " +
                    "X-Anonymous-Id 헤더가 있으면 그 익명 기록(저장·목표·기여)을 계정으로 병합하고 mergedRecordCount 를 돌려줍니다 — " +
                    "한 트랜잭션, 계정에 같은 제품이 있으면 건너뜀, 이미 병합된 ID 는 무시(0). " +
                    "토큰 교환·유저 조회 실패 401 LOGIN_FAILED, 지원하지 않는 provider 400. 탈퇴(is_active=false) 유저는 신규회원으로 처리.")
    @PostMapping("/oauth")
    public ResponseEntity<CommonResponse<TokenResponse>> login(Actor actor, @RequestBody OAuthLoginRequest request) {
        return ResponseEntity.ok(CommonResponse.success(authService.login(request, actor)));
    }

    // POST /auth/register — 폐지 (/auth/oauth 자동 가입으로 통합)

    // POST /auth/refresh
    @Operation(summary = "액세스 토큰 재발급",
            description = "없는 토큰 401 INVALID_TOKEN, 만료 401 TOKEN_EXPIRED(만료 행 삭제). 재발급 시 refresh 토큰 로테이션.")
    @PostMapping("/refresh")
    public ResponseEntity<CommonResponse<TokenResponse>> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(CommonResponse.success(authService.refresh(request)));
    }

    // POST /auth/logout
    @Operation(summary = "로그아웃",
            description = "토큰 필수(본인). 이 유저의 refresh 토큰을 전부 삭제하고 새 익명 ID 를 발급해 돌려줍니다. " +
                    "클라이언트는 기존(병합된) 익명 ID 를 버리고 이 값을 localStorage 에 저장합니다.")
    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<LogoutResponse>> logout(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(authService.logout(actor)));
    }
}
