package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.domain.coupang.entity.CoupangLink;

/**
 * 제품 상태 6종 (v3 DATA_003) — 저장하지 않고 조회할 때마다 정한다.
 * <ul>
 *   <li>점수를 매기지 않는 중분류(술 · 영유아식 · 환자용 식품, DATA_005)면 성분이 있든 없든 가장 먼저 「점수 제외」 —
 *       점수를 계산하지 않고 분석 요청으로도 보내지 않는다(11-17)</li>
 *   <li>생산 중단 표시(is_discontinued)가 있으면 쿠팡 확인 결과로 나눈다 — 판매 링크 있음 / 확인했는데 없음 / 아직 확인 전</li>
 *   <li>생산 중이면 판정 7종 성분이 다 있는지로 나눈다(ProductStatus)</li>
 * </ul>
 * 생산 중단 쪽을 성분보다 먼저 본다 — 성분이 빠진 단종 제품도 「단종」으로 보여야 사러 가지 않는다.
 */
public enum ProductState {

    SCORE_EXCLUDED("점수 제외"),
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
        return this == SCORE_EXCLUDED || this == PRODUCTION_STOPPED || this == STOCK_REMAINING || this == DISCONTINUED;
    }

    public static ProductState of(boolean scoreExcluded, ProductStatus status, boolean discontinued, CoupangLink link) {
        return link == null
                ? of(scoreExcluded, status, discontinued, null, null, null, 0)
                : of(scoreExcluded, status, discontinued, link.getLinkStatus(), link.getAffiliateUrl(), link.getLastSyncedAt(),
                     link.getMissedRefreshes());
    }

    /**
     * 목록 조회용 — 엔티티 없이 열 값으로. 생산 중단 제품은 쿠팡 연결로 가른다(DATA_003):
     * 지금 팔리는 연결이 있으면 재고 판매 중, 판매 상태를 확인 못 했거나(품절과 같게) 운영자 확인 대기면 생산 중단,
     * 찾아봤는데 연결이 없으면 단종, 아직 안 찾아봤으면 생산 중단.
     */
    public static ProductState of(boolean scoreExcluded, ProductStatus status, boolean discontinued,
                                  String linkStatus, String affiliateUrl, java.time.LocalDateTime lastSyncedAt,
                                  int missedRefreshes) {
        if (scoreExcluded) return SCORE_EXCLUDED;
        if (discontinued) {
            if (hasPurchaseLink(linkStatus, affiliateUrl)) {
                return missedRefreshes >= CoupangLink.SALE_UNKNOWN_AFTER ? PRODUCTION_STOPPED : STOCK_REMAINING;
            }
            if (CoupangLink.REVIEW.equals(linkStatus)) return PRODUCTION_STOPPED;
            if (lastSyncedAt != null) return DISCONTINUED;
            return PRODUCTION_STOPPED;
        }
        return status == ProductStatus.ANALYZED ? ANALYZED : NO_SCORE;
    }

    public static ProductState of(Product product) {
        return of(product.isScoreExcluded(), product.getStatus(), product.isDiscontinued(), product.getCoupangLink());
    }

    private static boolean hasPurchaseLink(String linkStatus, String affiliateUrl) {
        return CoupangLink.LINKED.equals(linkStatus) && affiliateUrl != null && !affiliateUrl.isBlank();
    }
}
