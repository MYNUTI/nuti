package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.domain.coupang.entity.CoupangLink;

/**
 * 제품 상태 5종 (v3 DATA_003) — 저장하지 않고 조회할 때마다 정한다.
 * <ul>
 *   <li>생산 중단 표시(is_discontinued)가 있으면 쿠팡 확인 결과로 나눈다 — 판매 링크 있음 / 확인했는데 없음 / 아직 확인 전</li>
 *   <li>생산 중이면 판정 7종 성분이 다 있는지로 나눈다(ProductStatus)</li>
 * </ul>
 * 생산 중단 쪽을 먼저 본다 — 성분이 빠진 단종 제품도 「단종」으로 보여야 사러 가지 않는다.
 */
public enum ProductState {

    NO_SCORE("점수 없음"),
    ANALYZED("분석 완료"),
    PRODUCTION_STOPPED("생산 중단"),
    STOCK_REMAINING("생산 중단·재고 판매 중"),
    DISCONTINUED("단종");

    private final String label;

    ProductState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 랭킹·더 맞는 제품·추천에서 뺀다. 검색·스캔·기록에는 남는다. */
    public boolean excludedFromRecommendations() {
        return this == PRODUCTION_STOPPED || this == STOCK_REMAINING || this == DISCONTINUED;
    }

    public static ProductState of(ProductStatus status, boolean discontinued, CoupangLink link) {
        return link == null
                ? of(status, discontinued, null, null, null)
                : of(status, discontinued, link.getLinkStatus(), link.getAffiliateUrl(), link.getLastSyncedAt());
    }

    /** 목록 조회용 — 엔티티 없이 열 값으로. */
    public static ProductState of(ProductStatus status, boolean discontinued,
                                  String linkStatus, String affiliateUrl, java.time.LocalDateTime lastSyncedAt) {
        if (discontinued) {
            if (hasPurchaseLink(linkStatus, affiliateUrl)) return STOCK_REMAINING;
            if (lastSyncedAt != null) return DISCONTINUED;
            return PRODUCTION_STOPPED;
        }
        return status == ProductStatus.ANALYZED ? ANALYZED : NO_SCORE;
    }

    public static ProductState of(Product product) {
        return of(product.getStatus(), product.isDiscontinued(), product.getCoupangLink());
    }

    private static boolean hasPurchaseLink(String linkStatus, String affiliateUrl) {
        return "LINKED".equals(linkStatus) && affiliateUrl != null && !affiliateUrl.isBlank();
    }
}
