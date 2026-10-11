package com.example.nutriuniv.domain.onboarding.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.onboarding.dto.OnboardingCompleteRequest;
import com.example.nutriuniv.domain.onboarding.dto.OnboardingCompleteResponse;
import com.example.nutriuniv.domain.onboarding.dto.SamplePickerResponse;
import com.example.nutriuniv.domain.onboarding.service.OnboardingService;
import com.example.nutriuniv.domain.onboarding.service.SamplePickerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Onboarding", description = "온보딩 — 샘플 픽커·완료 처리 (기능명세서 2.2, API 명세)")
@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final SamplePickerService samplePickerService;
    private final OnboardingService onboardingService;

    // GET /onboarding/samples
    @Operation(summary = "샘플 픽커 (명세 2.2)",
            description = "A등급 1개 + D등급 1개를 반드시 포함해 6~12개. 관리자 큐레이션 우선, 부족하면 조회수 상위. 분석 완료 제품만, 등급은 일반 기준. " +
                    "A 나 D 가 없으면 가장 먼 두 등급으로 대체하고 관리자 경고를 남깁니다. 못 채워도 있는 만큼(빈 배열 가능) — 실패해도 온보딩을 멈추지 않는 것은 클라 몫.")
    @GetMapping("/onboarding/samples")
    public ResponseEntity<CommonResponse<SamplePickerResponse>> samples(
            @Parameter(description = "개수 (기본 6, 최대 12)") @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(CommonResponse.success(samplePickerService.pick(size)));
    }

    // POST /onboarding/complete
    @Operation(summary = "온보딩 완료 처리",
            description = "X-Anonymous-Id 또는 토큰이 있으면 서버에 완료 시각을 저장(이미 완료면 200 멱등). 둘 다 없으면(동의 전) 저장할 ID 가 없어 " +
                    "persisted=false — 클라 localStorage 가 보조하고, 동의로 ID 가 생기면 다시 호출해 동기화합니다.")
    @PostMapping("/onboarding/complete")
    public ResponseEntity<CommonResponse<OnboardingCompleteResponse>> complete(
            Actor actor, @RequestBody(required = false) OnboardingCompleteRequest request) {
        return ResponseEntity.ok(CommonResponse.success(onboardingService.complete(actor, request)));
    }
}
