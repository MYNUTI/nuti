package com.example.nutriuniv.domain.app.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.app.dto.BootstrapResponse;
import com.example.nutriuniv.domain.app.service.BootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App", description = "앱 진입 API")
@RestController
@RequiredArgsConstructor
public class AppController {

    private final BootstrapService bootstrapService;

    // GET /app/bootstrap
    @Operation(summary = "앱 진입 초기화 (기능명세서 1.1)",
            description = "X-Anonymous-Id·Bearer 선택. 익명 ID를 발급하지 않습니다(발급은 POST /onboarding/consent). " +
                    "라우팅 분기는 onboardingCompleted만(안 함→찍기, 완료→홈). consentAgreed·healthConsented는 행동 시점 판단용, " +
                    "currentPolicyVersion은 동의 기록 버전과 비교해 행동 시점에 재동의를 유도합니다. 공유 링크 진입은 전역 리다이렉트 금지.")
    @GetMapping("/app/bootstrap")
    public ResponseEntity<CommonResponse<BootstrapResponse>> bootstrap(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(bootstrapService.bootstrap(actor)));
    }
}
