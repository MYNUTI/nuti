package com.example.nutriuniv.domain.me.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.me.dto.MeSettingsResponse;
import com.example.nutriuniv.domain.me.dto.MeSettingsUpdateRequest;
import com.example.nutriuniv.domain.me.dto.MeSummaryResponse;
import com.example.nutriuniv.domain.me.service.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Me", description = "마이·설정 화면 (API 명세 /me/summary · /me/settings)")
@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    // GET /me/summary
    @Operation(summary = "마이 화면 조회",
            description = "isLoggedIn · profile(비로그인 null → 「둘러보는 중이에요」) · goal · recordCount(저장 수, 익명 ID 기준도 집계) · " +
                    "contribution(접수 건수·처리 완료 수) · footerNotice(비로그인 로그인 권유). X-Anonymous-Id·토큰 선택.")
    @GetMapping("/me/summary")
    public ResponseEntity<CommonResponse<MeSummaryResponse>> summary(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(meService.summary(actor)));
    }

    // GET /me/settings
    @Operation(summary = "설정 화면 조회",
            description = "clipboardLinkDetection · goal · policies(현재 처리방침·약관 URL) · supportUrl · appVersion 을 한 번에. 소유자가 없으면 기본값.")
    @GetMapping("/me/settings")
    public ResponseEntity<CommonResponse<MeSettingsResponse>> settings(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(meService.settings(actor)));
    }

    // PATCH /me/settings
    @Operation(summary = "설정 변경",
            description = "X-Anonymous-Id 또는 토큰 필수(없으면 403 CONSENT_REQUIRED). 수정 필드 전무 400. goal 허용값 외 400, " +
                    "감량·근육 증가는 건강정보 동의 없으면 403 HEALTH_CONSENT_REQUIRED — 저장만 하고 재계산 응답은 없습니다. " +
                    "clipboardLinkDetection OFF 면 클라가 /links/resolve 를 호출하지 않습니다. 변경 후 설정 전체를 돌려줍니다.")
    @PatchMapping("/me/settings")
    public ResponseEntity<CommonResponse<MeSettingsResponse>> update(Actor actor, @RequestBody MeSettingsUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(meService.update(actor, request)));
    }
}
