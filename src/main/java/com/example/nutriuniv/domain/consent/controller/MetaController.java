package com.example.nutriuniv.domain.consent.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.consent.dto.PoliciesResponse;
import com.example.nutriuniv.domain.consent.entity.PolicyType;
import com.example.nutriuniv.domain.consent.repository.PolicyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meta", description = "약관·처리방침 메타")
@RestController
@RequiredArgsConstructor
public class MetaController {

    private final PolicyRepository policyRepository;

    // GET /meta/policies
    @Operation(summary = "약관·처리방침 현재 버전",
            description = "privacy·terms 의 현재 버전·URL·시행일. bootstrap 의 currentPolicyVersion 과 비교해 행동 시점에 재동의를 유도합니다. 정책 행이 없으면 해당 키가 빠집니다.")
    @GetMapping("/meta/policies")
    @Transactional(readOnly = true)
    public ResponseEntity<CommonResponse<PoliciesResponse>> policies() {
        PoliciesResponse body = PoliciesResponse.builder()
                .privacy(policyRepository.findByPolicyTypeAndIsCurrentTrue(PolicyType.PRIVACY).map(PoliciesResponse.Item::from).orElse(null))
                .terms(policyRepository.findByPolicyTypeAndIsCurrentTrue(PolicyType.TERMS).map(PoliciesResponse.Item::from).orElse(null))
                .build();
        return ResponseEntity.ok(CommonResponse.success(body));
    }
}
