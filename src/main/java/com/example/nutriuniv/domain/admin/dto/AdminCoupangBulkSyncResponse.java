package com.example.nutriuniv.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCoupangBulkSyncResponse {

    private int totalCount;
    private int successCount;
    private int reviewCount;     // 후보는 찾았는데 용량 · 개수를 확인 못 해 운영자 확인 대기(REVIEW)로 둔 수
    private int failCount;
}
