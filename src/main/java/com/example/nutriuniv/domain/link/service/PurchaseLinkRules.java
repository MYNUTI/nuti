package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import com.example.nutriuniv.domain.link.util.PackSize;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 구매 링크 판정 규칙 (DATA_006 · RSLT_009). 순수 함수 — 테스트로 고정.
 * <ul>
 *   <li>매칭 종류: 링크가 없거나 LINKED 가 아니면 NONE. 운영자 지정(coupang_links.match_type)이 있으면 그것.
 *       없으면 상품명의 1개 용량(g · mL)과 개수를 견준다 — 양쪽 다 적혀 있고 같으면 EXACT, 다르면 OTHER_SIZE,
 *       한쪽이라도 없으면 NAME_SEARCH(예전 연결 · 가격 없음, 검색 결과 페이지로)</li>
 *   <li>가격: EXACT 일 때만 판매가 · 조회일. 며칠 지나도 기준일과 함께 준다(11-17). 판매 상태를 확인 못 하면 주지 않는다</li>
 *   <li>100g(mL)당 · 1개당: 쿠팡 상품명에 1개 용량과 개수가 둘 다 읽힐 때만. 100g당이 10원 미만이거나 5만 원 초과면 뺀다(11-03).
 *       개수가 안 적혔으면 1개로 보지 않고 내지 않는다(11-24). g 상품과 mL 상품은 단위를 따로 준다</li>
 *   <li>URL: EXACT · OTHER_SIZE 는 상품 페이지(affiliate), NAME_SEARCH 와 판매 상태를 확인 못 한 링크는 그 제품 이름의 쿠팡 검색 결과(landing)</li>
 * </ul>
 */
public final class PurchaseLinkRules {

    public static final Duration PRICE_TTL = Duration.ofHours(24);
    public static final String AD_NOTICE = "쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.";
    static final int PER100_MIN = 10;
    static final int PER100_MAX = 50_000;

    private PurchaseLinkRules() {}

    public static PurchaseMatchType matchType(String linkStatus, String affiliateUrl, String overrideMatchType,
                                              String coupangProductName, String productName) {
        if (!"LINKED".equals(linkStatus) || affiliateUrl == null || affiliateUrl.isBlank()) return PurchaseMatchType.NONE;
        PurchaseMatchType override = PurchaseMatchType.fromOverride(overrideMatchType);
        if (override != null) return override;
        return switch (PackSize.compare(productName, coupangProductName)) {
            case SAME -> PurchaseMatchType.EXACT;
            case OTHER -> PurchaseMatchType.OTHER_SIZE;
            case UNKNOWN -> PurchaseMatchType.NAME_SEARCH;
        };
    }

    public static boolean priceFresh(LocalDateTime checkedAt, LocalDateTime now) {
        return checkedAt != null && !checkedAt.plus(PRICE_TTL).isBefore(now);
    }

    public static boolean priceVisible(PurchaseMatchType type, Integer price, boolean saleUnknown) {
        return type == PurchaseMatchType.EXACT && price != null && !saleUnknown;
    }

    /** 가격 조회일 — 새 컬럼(price_checked_at)이 비어 있는 예전 행은 마지막 동기화 시각. */
    public static LocalDateTime priceCheckedAt(LocalDateTime priceCheckedAt, LocalDateTime lastSyncedAt) {
        return priceCheckedAt != null ? priceCheckedAt : lastSyncedAt;
    }

    /** 100g(mL)당 값 — 범위를 벗어나면 null. */
    public static Integer per100(PackSize size, Integer price) {
        Integer v = size.per100(price);
        return v == null || v < PER100_MIN || v > PER100_MAX ? null : v;
    }

    public static String url(PurchaseMatchType type, String affiliateUrl, String landingUrl, boolean saleUnknown) {
        String search = landingUrl != null && !landingUrl.isBlank() ? landingUrl : affiliateUrl;
        return switch (type) {
            case NONE -> null;
            case EXACT, OTHER_SIZE -> saleUnknown ? search : affiliateUrl;
            case NAME_SEARCH -> search;
        };
    }
}
