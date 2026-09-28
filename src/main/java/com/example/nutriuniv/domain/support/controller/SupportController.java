package com.example.nutriuniv.domain.support.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.support.dto.InquiryCreateRequest;
import com.example.nutriuniv.domain.support.dto.InquiryCreateResponse;
import com.example.nutriuniv.domain.support.service.SupportInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Support", description = "문의하기 (API 명세 — 카카오 채널 대체 여부 팀 결정 필요)")
@RestController
@RequiredArgsConstructor
public class SupportController {

    private final SupportInquiryService supportInquiryService;

    // POST /support/inquiries
    @Operation(summary = "문의하기",
            description = "content 필수(빈 값·1000자 초과 400), category(20자)·contactEmail(형식 검사) 선택. 같은 사용자 하루 3건 초과 429. " +
                    "X-Anonymous-Id 선택 — 없으면 세션 기준으로 한도를 셉니다.")
    @PostMapping("/support/inquiries")
    public ResponseEntity<CommonResponse<InquiryCreateResponse>> submit(Actor actor, @RequestBody InquiryCreateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(supportInquiryService.submit(request, actor)));
    }
}
