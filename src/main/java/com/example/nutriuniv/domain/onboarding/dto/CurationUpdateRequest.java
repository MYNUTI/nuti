package com.example.nutriuniv.domain.onboarding.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** PUT /admin/curation/samples 입력 — 이 목록으로 교체(순서 = 노출 순서). 목록에 없는 기존 지정은 비활성화. */
@Getter
@NoArgsConstructor
public class CurationUpdateRequest {
    private List<Long> productIds;
}
