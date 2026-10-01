package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import com.example.nutriuniv.domain.link.util.CoupangTitleParser;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 구매 링크 판정 규칙 (기능명세서 8.1). 순수 함수 — 테스트로 고정.
 * <ul>
 *   <li>매칭 종류: 링크가 없거나 LINKED 가 아니면 NONE. 관리자 수동 지정(coupang_links.match_type)이 있으면 그것.
 *       없으면 <b>쿠팡 상품명(수량·용량 토막 제거)과 우리 제품명이 정규화 후 같을 때만 EXACT</b>, 그 외 NAME_SEARCH.
 *       저장된 링크가 전부 제품명 검색 결과라 보수적으로 간다 — 다른 제품 가격을 이 제품 가격처럼 보이게 하지 않는다.</li>
 *   <li>가격: EXACT 이고 마지막 조회가 24시간 이내일 때만. NAME_SEARCH 는 절대 주지 않는다.</li>
 *   <li>URL: EXACT 는 상품 페이지(affiliate), NAME_SEARCH 는 검색 결과 페이지(landing, 없으면 상품 페이지).</li>
 * </ul>
 */
public final class PurchaseLinkRules {

    public static final Duration PRICE_TTL = Duration.ofHours(24);
    public static final String AD_NOTICE = "쿠팡 파트너스 활동의 일환으로 일정액의 수수료를 받을 수 있어요. 노출비는 받지 않으며 순위·등급에 영향을 주지 않습니다.";

    private PurchaseLinkRules() {}

    public static PurchaseMatchType matchType(String linkStatus, String affiliateUrl, String overrideMatchType,
                                              String coupangProductName, String productName) {
        if (!"LINKED".equals(linkStatus) || affiliateUrl == null || affiliateUrl.isBlank()) return PurchaseMatchType.NONE;
        PurchaseMatchType override = PurchaseMatchType.fromOverride(overrideMatchType);
        if (override != null) return override;
        String ours = SearchNormalizer.normalize(productName);
        String theirs = SearchNormalizer.normalize(CoupangTitleParser.clean(coupangProductName));
        if (!ours.isEmpty() && ours.equals(theirs)) return PurchaseMatchType.EXACT;
        return PurchaseMatchType.NAME_SEARCH;
    }

    public static boolean priceFresh(LocalDateTime lastSyncedAt, LocalDateTime now) {
        return lastSyncedAt != null && !lastSyncedAt.plus(PRICE_TTL).isBefore(now);
    }

    public static boolean priceVisible(PurchaseMatchType type, Integer price, LocalDateTime lastSyncedAt, LocalDateTime now) {
        return type == PurchaseMatchType.EXACT && price != null && priceFresh(lastSyncedAt, now);
    }

    public static String url(PurchaseMatchType type, String affiliateUrl, String landingUrl) {
        return switch (type) {
            case NONE -> null;
            case EXACT -> affiliateUrl;
            case NAME_SEARCH -> landingUrl != null && !landingUrl.isBlank() ? landingUrl : affiliateUrl;
        };
    }
}
