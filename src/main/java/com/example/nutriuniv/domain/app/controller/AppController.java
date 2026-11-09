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
            description = "X-Anonymous-Id(기기 ID)·Bearer 선택. 서버는 익명 ID를 발급하지 않습니다 — 기기가 첫 실행 때 UUID 로 만듭니다. " +
                    "첫 실행 여부도 기기가 기억합니다. consentAgreed·healthConsented는 로그인한 계정의 동의 상태(기기 ID 만 있으면 false), " +
                    "currentPolicyVersion은 동의 기록 버전과 비교해 재동의를 유도합니다. 공유 링크 진입은 전역 리다이렉트 금지.")
    @GetMapping("/app/bootstrap")
    public ResponseEntity<CommonResponse<BootstrapResponse>> bootstrap(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(bootstrapService.bootstrap(actor)));
    }
}
