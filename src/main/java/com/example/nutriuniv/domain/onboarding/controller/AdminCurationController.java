package com.example.nutriuniv.domain.onboarding.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.onboarding.dto.CurationSamplesResponse;
import com.example.nutriuniv.domain.onboarding.dto.CurationUpdateRequest;
import com.example.nutriuniv.domain.onboarding.service.CurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Curation", description = "샘플 큐레이션 (기능명세서 10.1)")
@RestController
@RequiredArgsConstructor
public class AdminCurationController {

    private final CurationService curationService;

    // GET /admin/curation/samples
    @Operation(summary = "샘플 큐레이션 조회",
            description = "지정 목록 + 각 제품의 현재 등급(일반 기준, 조회 시 재계산). 경고: A등급 20건 미만 · A/D 없음 · 픽커 대체 발동 · 비활성 제품 자동 제외.")
    @GetMapping("/admin/curation/samples")
    public ResponseEntity<CommonResponse<CurationSamplesResponse>> list() {
        return ResponseEntity.ok(CommonResponse.success(curationService.list()));
    }

    // PUT /admin/curation/samples
    @Operation(summary = "샘플 큐레이션 지정",
            description = "productIds 순서대로 노출. 목록에 없는 기존 지정은 비활성화(빈 배열 = 전체 해제). 분석 완료 제품만 — 아니면 400, 없거나 비활성 제품 404.")
    @PutMapping("/admin/curation/samples")
    public ResponseEntity<CommonResponse<Void>> replace(Actor actor, @RequestBody CurationUpdateRequest request) {
        curationService.replace(request.getProductIds(), actor.userId());
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
