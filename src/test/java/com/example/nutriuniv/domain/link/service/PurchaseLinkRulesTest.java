package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.link.dto.PurchaseLinkResponse;
import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import com.example.nutriuniv.domain.link.util.PackSize;
import com.example.nutriuniv.domain.product.entity.Product;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** DATA_006 · RSLT_009 — 같은 용량 / 다른 용량 / 확인 안 됨, 가격 줄, 판매 상태 확인 못 함. */
class PurchaseLinkRulesTest {

    private static final String AFF = "https://link.coupang.com/a/abc";
    private static final String LANDING = "https://link.coupang.com/re/AFFSRP?q=x";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 12, 0);

    @Test
    void 링크_없거나_LINKED_아니면_NONE() {
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType(null, null, null, null, "닭가슴살 100g 1개"));
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType("FAILED", AFF, null, "닭가슴살, 100g, 1개", "닭가슴살 100g 1개"));
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType("REVIEW", AFF, null, "닭가슴살, 100g, 1개", "닭가슴살 100g 1개"));
        assertEquals(PurchaseMatchType.NONE, PurchaseLinkRules.matchType("LINKED", null, null, "닭가슴살, 100g, 1개", "닭가슴살 100g 1개"));
        assertNull(PurchaseLinkRules.url(PurchaseMatchType.NONE, AFF, LANDING, false));
    }

    @Test
    void 용량과_개수가_양쪽에_다_있고_같으면_같은_용량_다르면_다른_용량_없으면_확인_안_됨() {
        assertEquals(PurchaseMatchType.EXACT, PurchaseLinkRules.matchType("LINKED", AFF, null, "곰곰 닭가슴살, 100g, 1개", "곰곰 닭가슴살 100g 1개"));
        assertEquals(PurchaseMatchType.OTHER_SIZE, PurchaseLinkRules.matchType("LINKED", AFF, null, "곰곰 닭가슴살, 100g, 10개", "곰곰 닭가슴살 100g 1개"));
        assertEquals(PurchaseMatchType.OTHER_SIZE, PurchaseLinkRules.matchType("LINKED", AFF, null, "곰곰 닭가슴살, 130g, 1개", "곰곰 닭가슴살 100g 1개"));
        // 우리 이름에 용량이 없음 — 예전 연결은 가격 없이 검색 결과로
        assertEquals(PurchaseMatchType.NAME_SEARCH, PurchaseLinkRules.matchType("LINKED", AFF, null, "곰곰 통밀식빵, 700g, 1개", "곰곰 통밀 식빵"));
    }

    @Test
    void 운영자_지정이_있으면_그대로() {
        assertEquals(PurchaseMatchType.EXACT, PurchaseLinkRules.matchType("LINKED", AFF, "EXACT", "전혀 다른 이름", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.OTHER_SIZE, PurchaseLinkRules.matchType("LINKED", AFF, "other_size", "곰곰 통밀 식빵", "곰곰 통밀 식빵"));
        assertEquals(PurchaseMatchType.NAME_SEARCH, PurchaseLinkRules.matchType("LINKED", AFF, "garbage", "곰곰 통밀 식빵", "곰곰 통밀 식빵")); // 모르는 값은 자동 판정
    }

    @Test
    void 가격은_같은_용량이고_판매_상태가_확인될_때만() {
        assertTrue(PurchaseLinkRules.priceVisible(PurchaseMatchType.EXACT, 12900, false));
        assertFalse(PurchaseLinkRules.priceVisible(PurchaseMatchType.EXACT, 12900, true));
        assertFalse(PurchaseLinkRules.priceVisible(PurchaseMatchType.OTHER_SIZE, 12900, false));
        assertFalse(PurchaseLinkRules.priceVisible(PurchaseMatchType.NAME_SEARCH, 12900, false));
        assertFalse(PurchaseLinkRules.priceVisible(PurchaseMatchType.EXACT, null, false));
        assertTrue(PurchaseLinkRules.priceFresh(NOW.minusHours(24), NOW));
        assertFalse(PurchaseLinkRules.priceFresh(NOW.minusHours(24).minusSeconds(1), NOW));
        // 실패한 동기화가 last_synced_at 을 밀어도 가격 조회일은 price_checked_at
        assertEquals(NOW.minusDays(3), PurchaseLinkRules.priceCheckedAt(NOW.minusDays(3), NOW));
        assertEquals(NOW, PurchaseLinkRules.priceCheckedAt(null, NOW));
    }

    @Test
    void 버튼_주소() {
        assertEquals(AFF, PurchaseLinkRules.url(PurchaseMatchType.EXACT, AFF, LANDING, false));
        assertEquals(AFF, PurchaseLinkRules.url(PurchaseMatchType.OTHER_SIZE, AFF, LANDING, false));
        assertEquals(LANDING, PurchaseLinkRules.url(PurchaseMatchType.NAME_SEARCH, AFF, LANDING, false));
        assertEquals(AFF, PurchaseLinkRules.url(PurchaseMatchType.NAME_SEARCH, AFF, null, false));
        // 판매 상태 확인 못 함 — 품절 때처럼 그 제품 이름의 쿠팡 검색 결과로
        assertEquals(LANDING, PurchaseLinkRules.url(PurchaseMatchType.EXACT, AFF, LANDING, true));
    }

    @Test
    void 백g당은_10원_미만이나_5만원_초과면_뺀다() {
        assertEquals(1000, PurchaseLinkRules.per100(PackSize.parse("100g 1개"), 1000));
        assertNull(PurchaseLinkRules.per100(PackSize.parse("1kg 1개"), 50));          // 100g당 5원
        assertNull(PurchaseLinkRules.per100(PackSize.parse("10g 1개"), 6000));        // 100g당 6만 원
    }

    // ── 결과 화면 가격 줄 ──

    @Test
    void 같은_용량이면_판매가_기준일_백g당_1개당() {
        PurchaseLinkResponse r = PurchaseLinkService.of(product("곰곰 닭가슴살 100g 10개"),
                linked("곰곰 닭가슴살, 100g, 10개", 21500), NOW);
        assertEquals("EXACT", r.getMatchType());
        assertEquals("ON_SALE", r.getSaleStatus());
        assertEquals(AFF, r.getUrl());
        assertEquals(21500, r.getPrice());
        assertEquals(2150, r.getPer100Price());      // 21,500 ÷ (100 × 10) × 100
        assertEquals("g", r.getPer100Unit());
        assertEquals(2150, r.getUnitPrice());         // 21,500 ÷ 10
        assertNull(r.getBundleCount());
        assertNotNull(r.getPriceCheckedAt());
    }

    @Test
    void 다른_용량이면_묶음_수와_1개당만_판매가는_없음() {
        PurchaseLinkResponse r = PurchaseLinkService.of(product("곰곰 닭가슴살 100g 1개"),
                linked("곰곰 닭가슴살, 100g, 6개", 12900), NOW);
        assertEquals("OTHER_SIZE", r.getMatchType());
        assertEquals(AFF, r.getUrl());
        assertNull(r.getPrice());
        assertEquals(6, r.getBundleCount());
        assertEquals(2150, r.getUnitPrice());
        assertNull(r.getPer100Price());
        assertNotNull(r.getPriceCheckedAt());
    }

    @Test
    void 다른_용량인데_묶음_수를_못_읽으면_다른_용량만() {
        CoupangLink link = linked("곰곰 닭가슴살 1kg", 12900);
        link.overrideMatchType("OTHER_SIZE");
        PurchaseLinkResponse r = PurchaseLinkService.of(product("곰곰 닭가슴살"), link, NOW);
        assertEquals("OTHER_SIZE", r.getMatchType());
        assertNull(r.getBundleCount());
        assertNull(r.getUnitPrice());
        assertNull(r.getPrice());
        assertNull(r.getPriceCheckedAt());
    }

    @Test
    void 같은_용량으로_확인했는데_쿠팡_이름에_개수가_없으면_판매가만() {
        CoupangLink link = linked("곰곰 통밀 식빵 700g", 3500);
        link.overrideMatchType("EXACT");
        PurchaseLinkResponse r = PurchaseLinkService.of(product("곰곰 통밀 식빵"), link, NOW);
        assertEquals(3500, r.getPrice());
        assertNull(r.getPer100Price());    // 개수가 안 적혀 1개로 보지 않는다(11-24)
        assertNull(r.getUnitPrice());
    }

    @Test
    void 두_번_이어서_안_나오면_판매_상태_확인_못_함_가격_숨기고_검색_결과로() {
        CoupangLink link = linked("곰곰 닭가슴살, 100g, 10개", 21500);
        link.refreshMissed();
        assertEquals("ON_SALE", PurchaseLinkService.of(product("곰곰 닭가슴살 100g 10개"), link, NOW).getSaleStatus());
        link.refreshMissed();
        PurchaseLinkResponse r = PurchaseLinkService.of(product("곰곰 닭가슴살 100g 10개"), link, NOW);
        assertEquals("UNKNOWN", r.getSaleStatus());
        assertEquals(LANDING, r.getUrl());
        assertNull(r.getPrice());
        assertNull(r.getUnitPrice());
        assertNotNull(r.getAdNotice());     // 검색 결과도 제휴 링크라 고지는 그대로

        link.refreshFound("곰곰 닭가슴살, 100g, 10개", AFF, null, 20900, true, true);   // 다시 나오면 되돌린다
        assertEquals(20900, PurchaseLinkService.of(product("곰곰 닭가슴살 100g 10개"), link, NOW).getPrice());
    }

    @Test
    void 링크가_없으면_NONE() {
        PurchaseLinkResponse r = PurchaseLinkService.of(product("곰곰 닭가슴살"), null, NOW);
        assertEquals("NONE", r.getMatchType());
        assertNull(r.getUrl());
        assertNull(r.getSaleStatus());
    }

    private static Product product(String name) {
        return Product.create(name, null, null);
    }

    private static CoupangLink linked(String coupangName, int price) {
        CoupangLink link = CoupangLink.createDefault(product("x"));
        link.syncSuccess("1", coupangName, AFF, LANDING, null, price, true, true);
        return link;
    }
}
