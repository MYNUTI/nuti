package com.example.nutriuniv.domain.admin.dto;

import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.link.service.PurchaseLinkRules;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminCoupangLinkResponse {
    private Long productId;
    private String productName;
    private String coupangProductName;
    private String linkStatus;      // LINKED | UNLINKED | FAILED | REVIEW(운영자 확인 대기)
    private String matchType;       // LINKED · REVIEW 일 때 판정 — EXACT(같은 용량) | OTHER_SIZE(다른 용량) | NAME_SEARCH(용량 확인 안 됨)
    private String coupangUrl;      // 후보 · 연결 상품 페이지 (확인할 때 열어 보기)
    private Integer price;
    private Boolean isRocket;
    private int missedRefreshes;    // 가격을 새로 받을 때 연결한 상품이 이어서 안 나온 횟수
    private boolean saleUnknown;    // 2번 이상 — 판매 상태 확인 못 함(가격 숨김)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSyncedAt;

    public static AdminCoupangLinkResponse from(CoupangLink link) {
        return AdminCoupangLinkResponse.builder()
                .productId(link.getProduct().getId())
                .productName(link.getProduct().getName())
                .coupangProductName(link.getCoupangProductName())
                .linkStatus(link.getLinkStatus())
                .matchType(matchType(link))
                .coupangUrl(link.getAffiliateUrl())
                .price(link.getProductPrice())
                .isRocket(link.getIsRocket())
                .missedRefreshes(link.getMissedRefreshes())
                .saleUnknown(link.saleUnknown())
                .lastSyncedAt(link.getLastSyncedAt())
                .build();
    }

    /** 확인 대기 후보도 연결된 것처럼 판정해 보인다(운영자가 무엇을 확인할지 알게). */
    private static String matchType(CoupangLink link) {
        String status = link.getLinkStatus();
        if (!CoupangLink.LINKED.equals(status) && !CoupangLink.REVIEW.equals(status)) return null;
        return PurchaseLinkRules.matchType(CoupangLink.LINKED, link.getAffiliateUrl(), link.getMatchType(),
                link.getCoupangProductName(), link.getProduct().getName()).name();
    }
}
