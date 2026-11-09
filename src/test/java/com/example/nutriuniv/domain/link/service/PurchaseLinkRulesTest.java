package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 기능명세서 8.1 — 매칭 종류 구분, 가격은 EXACT + 24시간 이내만, 이름 검색 결과는 「검색해 보기」. */
class PurchaseLinkRulesTest {

    private static final String AFF = "https://link.coupang.com/a/abc";
    private static final String LANDING = "https://www.coupang.com/np/search?q=x";

    @Test
    void 링크_없거나_LINKED_아니면_NONE() {
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType(null, null, null, null, "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType("FAILED", AFF, null, "곰곰 통밀 식빵", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType("LINKED", null, null, "곰곰 통밀 식빵", "곰곰 통밀 식빵"));
        assertNull(PurchaseLinkRules.url(PurchaseMatchType.NONE, AFF, LANDING));
    }

    @Test
    void 이름이_정규화_후_같을_때만_EXACT() {
        assertEquals(PurchaseMatchType.EXACT, PurchaseLinkRules.matchType("LINKED", AFF, null, "곰곰 통밀식빵, 700g, 1개", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.NAME_SEARCH, PurchaseLinkRules.matchType("LINKED", AFF, null, "곰곰 통밀 식빵 골드", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.NAME_SEARCH, PurchaseLinkRules.matchType("LINKED", AFF, null, null, "곰곰 통밀 식빵"));
    }

    @Test
    void 수동_지정이_있으면_그대로() {
        assertEquals(PurchaseMatchType.EXACT, PurchaseLinkRules.matchType("LINKED", AFF, "EXACT", "전혀 다른 이름", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.NAME_SEARCH, PurchaseLinkRules.matchType("LINKED", AFF, "name_search", "곰곰 통밀 식빵", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.EXACT, PurchaseLinkRules.matchType("LINKED", AFF, "garbage", "곰곰 통밀 식빵", "곰곰 통밀 식빵")); // 모르는 값은 자동 판정
    }

    @Test
    void 가격은_EXACT_이면_직전_판매가와_조회일_24시간이_지나면_stale() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);
        assertTrue(PurchaseLinkRules.priceVisible(PurchaseMatchType.EXACT, 12900));
        assertFalse(PurchaseLinkRules.priceVisible(PurchaseMatchType.NAME_SEARCH, 12900));
        assertFalse(PurchaseLinkRules.priceVisible(PurchaseMatchType.EXACT, null));
        assertTrue(PurchaseLinkRules.priceFresh(now.minusHours(24), now));
        assertFalse(PurchaseLinkRules.priceFresh(now.minusHours(24).minusSeconds(1), now));
        assertFalse(PurchaseLinkRules.priceFresh(null, now));
        // 실패한 동기화가 last_synced_at 을 밀어도 가격 조회일은 price_checked_at
        assertEquals(now.minusDays(3), PurchaseLinkRules.priceCheckedAt(now.minusDays(3), now));
        assertEquals(now, PurchaseLinkRules.priceCheckedAt(null, now));
    }

    @Test
    void 이름_검색은_검색_결과_페이지로() {
        assertEquals(AFF, PurchaseLinkRules.url(PurchaseMatchType.EXACT, AFF, LANDING));
        assertEquals(LANDING, PurchaseLinkRules.url(PurchaseMatchType.NAME_SEARCH, AFF, LANDING));
        assertEquals(AFF, PurchaseLinkRules.url(PurchaseMatchType.NAME_SEARCH, AFF, null));
    }
}
