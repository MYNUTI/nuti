package com.example.nutriuniv.domain.body.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.body.dto.BodyProfileRequest;
import com.example.nutriuniv.domain.body.dto.BodyProfileResponse;
import com.example.nutriuniv.domain.body.service.BodyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "BodyProfile", description = "몸 정보 (v3 ACCT_010)")
@RestController
@RequestMapping("/me/body-profile")
@RequiredArgsConstructor
public class BodyProfileController {

    private final BodyProfileService bodyProfileService;

    @Operation(summary = "몸 정보 조회",
            description = "로그인 필수(401), 「몸 정보 수집·이용」 동의 필수(403 BODY_CONSENT_REQUIRED). 저장 전이면 saved=false · kcalBand=2000.")
    @GetMapping
    public ResponseEntity<CommonResponse<BodyProfileResponse>> get(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(bodyProfileService.get(actor)));
    }

    @Operation(summary = "몸 정보 저장 (내 몸에 맞추기)",
            description = "성별·출생 연도·키·체중·활동량 4단계·하루 필요 칼로리(기기가 계산). 서버가 가장 가까운 칼로리 구간(1,500·2,000·2,500·3,000, 정확히 중간이면 위)을 정해 저장합니다. " +
                    "범위: 출생 연도 1900~만 14세가 되는 해, 키 100~250cm, 체중 25~250kg, 하루 칼로리 500~6000 — 벗어나면 400 VALIDATION_FAILED. " +
                    "저장 뒤 결과·검색·랭킹 등은 kcalBand 파라미터 대신 저장한 구간을 씁니다.")
    @PutMapping
    public ResponseEntity<CommonResponse<BodyProfileResponse>> save(Actor actor, @RequestBody BodyProfileRequest request) {
        return ResponseEntity.ok(CommonResponse.success(bodyProfileService.save(actor, request)));
    }

    @Operation(summary = "몸 정보 지우기", description = "값만 지우고 기본 2,000kcal 로 돌아갑니다. 동의 철회는 PUT /me/consents/BODY_PROFILE.")
    @DeleteMapping
    public ResponseEntity<CommonResponse<BodyProfileResponse>> clear(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(bodyProfileService.clear(actor)));
    }
}
