package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 상태 6종 (v3 DATA_003) — 점수 제외를 가장 먼저, 다음은 생산 중단이면 쿠팡 확인 결과로, 아니면 성분으로. */
class ProductStateTest {

    private static final Product P = Product.create("제품", null, null);

    @Test
    void 생산_중이면_성분으로_나눈다() {
        assertEquals(ProductState.ANALYZED, ProductState.of(false, ProductStatus.ANALYZED, false, null));
        assertEquals(ProductState.NO_SCORE, ProductState.of(false, ProductStatus.INSUFFICIENT, false, null));
        assertEquals(ProductState.NO_SCORE, ProductState.of(false, null, false, null));
    }

    @Test
    void 점수_제외는_성분이_있든_없든_생산_중단이어도_가장_먼저() {
        assertEquals(ProductState.SCORE_EXCLUDED, ProductState.of(true, ProductStatus.ANALYZED, false, null));
        assertEquals(ProductState.SCORE_EXCLUDED, ProductState.of(true, ProductStatus.INSUFFICIENT, false, null));
        assertEquals(ProductState.SCORE_EXCLUDED, ProductState.of(true, ProductStatus.ANALYZED, true, CoupangLink.createDefault(P)));
    }

    @Test
    void 제품의_소속_중분류로_점수_제외를_가른다() {
        Category etc = Category.seeded("99", "기타", null, 16, true, false);
        Category liquor = Category.seeded("9901", "술", etc, 1, true, true);
        Category unsorted = Category.seeded("9904", "분류 못 정함", etc, 4, true, false);

        Product a = Product.create("소주", null, null);
        a.applyLoad("소주", liquor, null, null, null, null, null, false, false, "h");
        Product b = Product.create("모름", null, null);
        b.applyLoad("모름", unsorted, null, null, null, null, null, false, false, "h");

        assertTrue(a.isScoreExcluded());
        assertFalse(b.isScoreExcluded());   // 분류 못 정함은 점수를 매긴다(랭킹에만 없음)
        assertFalse(P.isScoreExcluded());   // 분류 없음
    }

    @Test
    void 생산_중단_쿠팡_확인_전() {
        assertEquals(ProductState.PRODUCTION_STOPPED, ProductState.of(false, ProductStatus.ANALYZED, true, null));
        assertEquals(ProductState.PRODUCTION_STOPPED, ProductState.of(false, ProductStatus.ANALYZED, true, CoupangLink.createDefault(P)));
    }

    @Test
    void 생산_중단_판매_링크_있음은_재고_판매_중() {
        CoupangLink link = CoupangLink.createDefault(P);
        link.syncSuccess("1", "쿠팡 이름", "https://link.coupang.com/a/x", "https://www.coupang.com/vp/products/1", null, 1000, true, true);
        assertEquals(ProductState.STOCK_REMAINING, ProductState.of(false, ProductStatus.ANALYZED, true, link));
    }

    @Test
    void 생산_중단_확인했는데_링크_없음은_단종_성분이_없어도_단종() {
        CoupangLink link = CoupangLink.createDefault(P);
        link.syncFailed();
        assertEquals(ProductState.DISCONTINUED, ProductState.of(false, ProductStatus.INSUFFICIENT, true, link));
    }

    @Test
    void 점수_제외와_생산_중단_쪽은_추천에서_뺀다() {
        assertFalse(ProductState.ANALYZED.excludedFromRecommendations());
        assertFalse(ProductState.NO_SCORE.excludedFromRecommendations());
        assertTrue(ProductState.SCORE_EXCLUDED.excludedFromRecommendations());
        assertTrue(ProductState.PRODUCTION_STOPPED.excludedFromRecommendations());
        assertTrue(ProductState.STOCK_REMAINING.excludedFromRecommendations());
        assertTrue(ProductState.DISCONTINUED.excludedFromRecommendations());
    }
}
