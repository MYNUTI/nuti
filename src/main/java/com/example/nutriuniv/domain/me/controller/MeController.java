package com.example.nutriuniv.domain.me.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.me.dto.ContributionsResponse;
import com.example.nutriuniv.domain.me.dto.MeSettingsResponse;
import com.example.nutriuniv.domain.me.dto.MeSettingsUpdateRequest;
import com.example.nutriuniv.domain.me.dto.MeSummaryResponse;
import com.example.nutriuniv.domain.me.service.ContributionService;
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
    private final ContributionService contributionService;

    // GET /me/summary
    @Operation(summary = "마이 화면 조회",
            description = "isLoggedIn · profile(비로그인 null → 「둘러보는 중이에요」) · goal · recordCount(저장 수, 익명 ID 기준도 집계) · " +
                    "contribution(접수 건수·처리 완료 수) · footerNotice(비로그인 로그인 권유). X-Anonymous-Id·토큰 선택.")
    @GetMapping("/me/summary")
    public ResponseEntity<CommonResponse<MeSummaryResponse>> summary(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(meService.summary(actor)));
    }

    // GET /me/contributions
    @Operation(summary = "내 기여 (v3 CTRB_006)",
            description = "내가 직접 보낸 분석 요청(자동 등록 제외) · 제보 목록과 상태(분석 중 · 확인 중 · 완료 · 분석 못 함 / 확인 중 · 반영 · 반영하지 않음), summary 건수. " +
                    "토큰 또는 X-Anonymous-Id 기준, 둘 다 없으면 빈 목록.")
    @GetMapping("/me/contributions")
    public ResponseEntity<CommonResponse<ContributionsResponse>> contributions(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(contributionService.list(actor)));
    }

    // GET /me/settings
    @Operation(summary = "설정 화면 조회",
            description = "goal · policies(현재 처리방침·약관 URL) · supportUrl · appVersion 을 한 번에. 소유자가 없으면 기본값.")
    @GetMapping("/me/settings")
    public ResponseEntity<CommonResponse<MeSettingsResponse>> settings(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(meService.settings(actor)));
    }

    // PATCH /me/settings
    @Operation(summary = "설정 변경",
            description = "X-Anonymous-Id(기기 ID) 또는 토큰 필수(없으면 400 ANONYMOUS_ID_REQUIRED). goal 이 없거나 허용값 외면 400. " +
                    "저장만 하고 재계산 응답은 없습니다. 변경 후 설정 전체를 돌려줍니다.")
    @PatchMapping("/me/settings")
    public ResponseEntity<CommonResponse<MeSettingsResponse>> update(Actor actor, @RequestBody MeSettingsUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(meService.update(actor, request)));
    }
}
