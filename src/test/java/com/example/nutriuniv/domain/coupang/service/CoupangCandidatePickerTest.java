package com.example.nutriuniv.domain.coupang.service;

import com.example.nutriuniv.domain.admin.service.AdminCoupangService;
import com.example.nutriuniv.domain.coupang.dto.CoupangProductData;
import com.example.nutriuniv.domain.coupang.dto.CoupangSearchResponse;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.service.CoupangCandidatePicker.Decision;
import com.example.nutriuniv.domain.product.entity.Product;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 쿠팡 연결 고르기 (DATA_006 · 11-24) — 이름 유사도 문턱, 같은 용량 → 다른 용량 → 운영자 확인. 가격 새로 받기. */
class CoupangCandidatePickerTest {

    @Test
    void 같은_용량을_먼저_고른다_이름_점수가_조금_낮아도() {
        List<CoupangProductData> c = List.of(
                item(1, "곰곰 닭가슴살 100g 10개"),            // 이름 100점 · 다른 용량
                item(2, "곰곰 닭가슴살 오리지널, 100g, 1개"));   // 이름 80점 · 같은 용량
        CoupangCandidatePicker.Pick p = CoupangCandidatePicker.pick(c, "곰곰 닭가슴살 100g 1개");
        assertEquals(Decision.SAME, p.decision());
        assertEquals(2L, p.data().getProductId());
    }

    @Test
    void 같은_용량이_없으면_다른_용량() {
        CoupangCandidatePicker.Pick p = CoupangCandidatePicker.pick(List.of(
                item(1, "곰곰 닭가슴살, 100g, 10개"),
                item(2, "전혀 다른 과자, 100g, 1개")), "곰곰 닭가슴살 100g 1개");
        assertEquals(Decision.OTHER, p.decision());
        assertEquals(1L, p.data().getProductId());
    }

    @Test
    void 한쪽이라도_용량이나_개수가_없으면_운영자_확인() {
        CoupangCandidatePicker.Pick p = CoupangCandidatePicker.pick(List.of(item(1, "곰곰 통밀 식빵, 700g, 1개")), "곰곰 통밀 식빵");
        assertEquals(Decision.REVIEW, p.decision());
        assertEquals(1L, p.data().getProductId());
    }

    @Test
    void 이름_점수가_문턱_아래면_고르지_않는다() {
        // 같은 용량이어도 이름이 안 맞으면 다른 제품이다
        assertEquals(Decision.NONE, CoupangCandidatePicker.pick(
                List.of(item(1, "전혀 다른 과자, 100g, 1개")), "곰곰 닭가슴살 100g 1개").decision());
        assertEquals(Decision.NONE, CoupangCandidatePicker.pick(List.of(), "곰곰 닭가슴살").decision());
    }

    @Test
    void 키워드_줄이기() {
        assertEquals(List.of("꼬기다 닭가슴살 갈릭크림맛", "꼬기다 닭가슴살", "꼬기다"),
                CoupangCandidatePicker.keywordVariants("꼬기다 닭가슴살 갈릭크림맛"));
        assertEquals(List.of("두부"), CoupangCandidatePicker.keywordVariants("두부"));
    }

    // ── 가격 새로 받기 ──

    @Test
    void 연결한_상품이_다시_나오면_판매가를_새로_받고_안_나온_횟수를_지운다() {
        CoupangLink link = linked();
        link.refreshMissed();
        assertTrue(AdminCoupangService.refreshOne(link, data(item(9, "다른 것"), item(7, "곰곰 닭가슴살, 100g, 10개", 19900))));
        assertEquals(19900, link.getProductPrice());
        assertEquals(0, link.getMissedRefreshes());
    }

    @Test
    void 두_번_이어서_안_나오면_판매_상태_확인_못_함_직전_판매가는_그대로() {
        CoupangLink link = linked();
        assertFalse(AdminCoupangService.refreshOne(link, data(item(9, "다른 것"))));
        assertFalse(link.saleUnknown());
        assertFalse(AdminCoupangService.refreshOne(link, data()));   // 검색 0건도 「안 나옴」
        assertTrue(link.saleUnknown());
        assertEquals(21500, link.getProductPrice());
        assertEquals(CoupangLink.LINKED, link.getLinkStatus());           // 연결은 지우지 않는다
    }

    private static CoupangLink linked() {
        CoupangLink link = CoupangLink.createDefault(Product.create("곰곰 닭가슴살 100g 10개", null, null));
        link.syncSuccess("7", "곰곰 닭가슴살, 100g, 10개", "https://link.coupang.com/a/x", null, null, 21500, true, true);
        return link;
    }

    private static CoupangSearchResponse.SearchData data(CoupangProductData... items) {
        CoupangSearchResponse.SearchData d = new CoupangSearchResponse.SearchData();
        d.setProductData(List.of(items));
        return d;
    }

    private static CoupangProductData item(long id, String name) {
        return item(id, name, 10000);
    }

    private static CoupangProductData item(long id, String name, int price) {
        CoupangProductData d = new CoupangProductData();
        d.setProductId(id);
        d.setProductName(name);
        d.setProductPrice(price);
        d.setProductUrl("https://link.coupang.com/a/" + id);
        return d;
    }
}
