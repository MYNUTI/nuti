package com.example.nutriuniv.domain.admin.dto;

import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminCoupangSyncResponse {
    private String linkStatus;
    private String matchType;       // 운영자 지정값(확인 결과) — 비어 있으면 상품명으로 자동 판정
    private String affiliateUrl;
    private Integer price;
    private int missedRefreshes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSyncedAt;

    public static AdminCoupangSyncResponse from(CoupangLink link) {
        return AdminCoupangSyncResponse.builder()
                .linkStatus(link.getLinkStatus())
                .matchType(link.getMatchType())
                .affiliateUrl(link.getAffiliateUrl())
                .price(link.getProductPrice())
                .missedRefreshes(link.getMissedRefreshes())
                .lastSyncedAt(link.getLastSyncedAt())
                .build();
    }
}
