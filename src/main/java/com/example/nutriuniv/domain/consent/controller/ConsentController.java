package com.example.nutriuniv.domain.consent.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.consent.dto.ConsentRequest;
import com.example.nutriuniv.domain.consent.dto.ConsentResponse;
import com.example.nutriuniv.domain.consent.dto.HealthConsentResponse;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Consent", description = "개인정보·건강정보 동의 API")
@RestController
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    // POST /onboarding/consent
    @Operation(summary = "개인정보 수집·이용 동의 (기능명세서 1.2)",
            description = "로그인 필수(없으면 401). 계정에 처리방침 동의를 남깁니다. 익명 ID 는 발급하지 않습니다 — v3 부터 기기가 만들고 동의 없이 씁니다. " +
                    "같은 버전 재요청은 200 멱등. 필수 항목 누락 400, 정책 버전 불일치 409 POLICY_VERSION_CONFLICT. 동의 기록은 append-only, IP는 해시만 저장.")
    @PostMapping("/onboarding/consent")
    public ResponseEntity<CommonResponse<ConsentResponse>> consentPrivacy(
            Actor actor,
            @Valid @RequestBody ConsentRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        return ResponseEntity.ok(CommonResponse.success(consentService.consentPrivacy(actor, request, userAgent)));
    }

    // POST /me/health-consent
    @Operation(summary = "건강정보 수집·이용 동의 (기능명세서 1.3)",
            description = "로그인 필수(없으면 401). 개인정보 동의와 저장 위치를 분리합니다.")
    @PostMapping("/me/health-consent")
    public ResponseEntity<CommonResponse<HealthConsentResponse>> agreeHealth(
            Actor actor,
            @Valid @RequestBody ConsentRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        return ResponseEntity.ok(CommonResponse.success(consentService.agreeHealth(actor, request, userAgent)));
    }

    // GET /me/health-consent
    @Operation(summary = "건강정보 동의 조회",
            description = "로그인 필수(없으면 401). 「동의 안 함」은 오류가 아니라 healthConsented=false 인 정상 응답입니다.")
    @GetMapping("/me/health-consent")
    public ResponseEntity<CommonResponse<HealthConsentResponse>> getHealth(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(consentService.getHealth(actor)));
    }

    // DELETE /me/health-consent
    @Operation(summary = "건강정보 동의 철회",
            description = "로그인 필수(없으면 401). 철회 기록을 남기고(append-only) 건강정보 플래그를 내립니다. 목표는 GENERAL로 되돌아갑니다. 이미 철회 상태면 200 멱등.")
    @DeleteMapping("/me/health-consent")
    public ResponseEntity<CommonResponse<HealthConsentResponse>> revokeHealth(
            Actor actor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        return ResponseEntity.ok(CommonResponse.success(consentService.revokeHealth(actor, userAgent)));
    }
}
