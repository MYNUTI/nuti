package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 상태 5종 (v3 DATA_003) — 생산 중단이면 쿠팡 확인 결과로, 아니면 성분으로. */
class ProductStateTest {

    private static final Product P = Product.create("제품", null, null);

    @Test
    void 생산_중이면_성분으로_나눈다() {
        assertEquals(ProductState.ANALYZED, ProductState.of(ProductStatus.ANALYZED, false, null));
        assertEquals(ProductState.NO_SCORE, ProductState.of(ProductStatus.INSUFFICIENT, false, null));
        assertEquals(ProductState.NO_SCORE, ProductState.of(null, false, null));
    }

    @Test
    void 생산_중단_쿠팡_확인_전() {
        assertEquals(ProductState.PRODUCTION_STOPPED, ProductState.of(ProductStatus.ANALYZED, true, null));
        assertEquals(ProductState.PRODUCTION_STOPPED, ProductState.of(ProductStatus.ANALYZED, true, CoupangLink.createDefault(P)));
    }

    @Test
    void 생산_중단_판매_링크_있음은_재고_판매_중() {
        CoupangLink link = CoupangLink.createDefault(P);
        link.syncSuccess("1", "쿠팡 이름", "https://link.coupang.com/a/x", "https://www.coupang.com/vp/products/1", null, 1000, true, true);
        assertEquals(ProductState.STOCK_REMAINING, ProductState.of(ProductStatus.ANALYZED, true, link));
    }

    @Test
    void 생산_중단_확인했는데_링크_없음은_단종_성분이_없어도_단종() {
        CoupangLink link = CoupangLink.createDefault(P);
        link.syncFailed();
        assertEquals(ProductState.DISCONTINUED, ProductState.of(ProductStatus.INSUFFICIENT, true, link));
    }

    @Test
    void 생산_중단_쪽만_추천에서_뺀다() {
        assertFalse(ProductState.ANALYZED.excludedFromRecommendations());
        assertFalse(ProductState.NO_SCORE.excludedFromRecommendations());
        assertTrue(ProductState.PRODUCTION_STOPPED.excludedFromRecommendations());
        assertTrue(ProductState.STOCK_REMAINING.excludedFromRecommendations());
        assertTrue(ProductState.DISCONTINUED.excludedFromRecommendations());
    }
}
