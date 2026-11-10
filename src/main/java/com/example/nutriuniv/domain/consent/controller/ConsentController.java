package com.example.nutriuniv.domain.consent.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.consent.dto.ConsentChangeRequest;
import com.example.nutriuniv.domain.consent.dto.ConsentsResponse;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Consent", description = "동의 관리 (v3 ACCT_007)")
@RestController
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @Operation(summary = "내 동의 상태",
            description = "로그인 필수(401). 항목 6개(AGE_14·TERMS·PRIVACY 필수, GOAL_ACCOUNT·BODY_PROFILE·MARKETING 선택)의 지금 상태와 바꾼 시각·판.")
    @GetMapping("/me/consents")
    public ResponseEntity<CommonResponse<ConsentsResponse>> list(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(consentService.list(actor)));
    }

    @Operation(summary = "선택 동의 켜기·철회",
            description = "로그인 필수(401). 선택 항목만(필수 항목 400). 같은 상태면 기록하지 않고 그대로 돌려줍니다. " +
                    "철회하면 그 동의로 저장한 데이터를 바로 지웁니다 — GOAL_ACCOUNT: 계정 목표, BODY_PROFILE: 몸 정보·칼로리 구간(기본 2,000 으로), MARKETING: 기록만. " +
                    "켤 때 GOAL_ACCOUNT 는 goal, BODY_PROFILE 은 bodyProfile 을 함께 보내면 계정에 저장합니다. documentVersion 은 동의문 판.")
    @PutMapping("/me/consents/{item}")
    public ResponseEntity<CommonResponse<ConsentsResponse>> change(
            Actor actor,
            @Parameter(description = "GOAL_ACCOUNT | BODY_PROFILE | MARKETING") @PathVariable String item,
            @RequestBody ConsentChangeRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(CommonResponse.success(consentService.change(actor, item, request, userAgent)));
    }
}
