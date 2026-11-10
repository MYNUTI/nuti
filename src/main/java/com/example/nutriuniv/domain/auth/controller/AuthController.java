package com.example.nutriuniv.domain.auth.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.auth.dto.OAuthLoginRequest;
import com.example.nutriuniv.domain.auth.dto.RefreshRequest;
import com.example.nutriuniv.domain.auth.dto.SignupRequest;
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
    @Operation(summary = "소셜 로그인 (v3 ACCT_004 · ACCT_005)",
            description = "가입한 계정이면 토큰을 주고, X-Anonymous-Id(기기 ID) 기록(즐겨찾기·최근 본 제품·분석 요청·제보, 동의했으면 목표)을 계정으로 옮겨 " +
                    "mergedRecordCount·merged(종류별)를 돌려줍니다. 같은 제품 즐겨찾기는 하나로 합칩니다. 옮기다 실패해도 로그인은 끝납니다(다음 로그인 때 다시). " +
                    "처음 로그인한 계정이면 계정을 만들지 않고 signupRequired=true · signupToken(30분) · signupProfile 만 줍니다 — " +
                    "가입 동의 화면을 거쳐 POST /auth/signup 으로 가입합니다. 토큰 교환·유저 조회 실패 401 LOGIN_FAILED, 지원하지 않는 provider 400.")
    @PostMapping("/oauth")
    public ResponseEntity<CommonResponse<TokenResponse>> login(Actor actor, @RequestBody OAuthLoginRequest request) {
        return ResponseEntity.ok(CommonResponse.success(authService.login(request, actor)));
    }

    // POST /auth/signup
    @Operation(summary = "가입 (v3 ACCT_004 「동의하고 시작하기」)",
            description = "signupToken(POST /auth/oauth 가 준 것) + 동의 6항목. 필수 AGE_14·TERMS·PRIVACY 가 하나라도 false 면 400 REQUIRED_CONSENT_MISSING(가입 안 함). " +
                    "토큰 만료·위조 401 SIGNUP_TOKEN_INVALID(다시 로그인). 약관·처리방침 판이 지금 판과 다르면 409 POLICY_VERSION_CONFLICT. " +
                    "항목마다 동의 여부·시각·판을 기록하고, GOAL_ACCOUNT 에 동의했으면 goal 을 계정에 저장합니다. 기기 기록 병합은 로그인과 같습니다. " +
                    "몸 정보 값은 가입 뒤 PUT /me/body-profile 로 보냅니다(5-7 「저장하고 시작하기」).")
    @PostMapping("/signup")
    public ResponseEntity<CommonResponse<TokenResponse>> signup(
            Actor actor,
            @RequestBody SignupRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(CommonResponse.success(authService.signup(request, actor, userAgent)));
    }

    // POST /auth/refresh
    @Operation(summary = "액세스 토큰 재발급",
            description = "없는 토큰 401 INVALID_TOKEN, 만료 401 TOKEN_EXPIRED(만료 행 삭제). 재발급 시 refresh 토큰 로테이션.")
    @PostMapping("/refresh")
    public ResponseEntity<CommonResponse<TokenResponse>> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(CommonResponse.success(authService.refresh(request)));
    }

    // POST /auth/logout
    @Operation(summary = "로그아웃",
            description = "토큰 필수(본인). 이 유저의 refresh 토큰을 전부 삭제합니다. 서버는 익명 ID 를 발급하지 않습니다 — " +
                    "로그아웃 뒤 새 기기 ID 를 만들지는 클라이언트가 정합니다(명세 결정 대기, 추천: 새로 만든다).")
    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<Void>> logout(Actor actor) {
        authService.logout(actor);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
