package com.example.nutriuniv.domain.link.util;

import com.example.nutriuniv.domain.link.util.LinkUrlNormalizer.Parsed;
import com.example.nutriuniv.domain.link.util.LinkUrlNormalizer.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** API 명세 /links/resolve — utm_·제휴코드 제거, 쿠팡 상품 ID 추출, 같은 링크는 같은 canonical. */
class LinkUrlNormalizerTest {

    @Test
    void 쿠팡_상품_링크는_상품ID로_정규화() {
        Parsed p = LinkUrlNormalizer.parse(
                "https://www.coupang.com/vp/products/7654321?itemId=111&vendorItemId=222&src=1139000&spec=10799999&addtag=400&ctag=7654321&lptag=AF1234&itime=20260930&pageType=PRODUCT&traceid=V0-153&utm_source=kakao#detail");
        assertEquals(SourceType.COUPANG, p.sourceType());
        assertEquals("7654321", p.coupangProductId());
        assertFalse(p.shortLink());
        assertEquals("https://www.coupang.com/vp/products/7654321?itemId=111&vendorItemId=222", p.canonical());
    }

    @Test
    void 모바일_링크와_스킴_없는_링크도_같은_canonical() {
        Parsed mobile = LinkUrlNormalizer.parse("https://m.coupang.com/vm/products/7654321?itemId=111&vendorItemId=222&q=단백질");
        Parsed bare = LinkUrlNormalizer.parse("www.coupang.com/vp/products/7654321?vendorItemId=222&itemId=111");
        assertEquals("https://www.coupang.com/vp/products/7654321?itemId=111&vendorItemId=222", mobile.canonical());
        assertEquals(mobile.canonical(), bare.canonical());
        assertEquals(LinkUrlNormalizer.sha256Hex(mobile.canonical()), LinkUrlNormalizer.sha256Hex(bare.canonical()));
    }

    @Test
    void 단축_링크는_상품ID_없이_shortLink() {
        Parsed p = LinkUrlNormalizer.parse("https://link.coupang.com/a/bXyZ12");
        assertEquals(SourceType.COUPANG, p.sourceType());
        assertNull(p.coupangProductId());
        assertTrue(p.shortLink());
        assertTrue(LinkUrlNormalizer.parse("https://coupa.ng/cfABCD").shortLink());
    }

    @Test
    void 쿠팡이_아니면_UNKNOWN_추적_파라미터만_제거() {
        Parsed p = LinkUrlNormalizer.parse("https://smartstore.naver.com/brand/products/123?NaPm=ct%3Dx&utm_medium=cpc&utm_campaign=a&fbclid=abc&page=2#top");
        assertEquals(SourceType.UNKNOWN, p.sourceType());
        assertNull(p.coupangProductId());
        assertEquals("https://smartstore.naver.com/brand/products/123?NaPm=ct%3Dx&page=2", p.canonical());
    }

    @Test
    void 호스트_판정은_하위_도메인만() {
        assertTrue(LinkUrlNormalizer.isCoupangHost("www.coupang.com"));
        assertTrue(LinkUrlNormalizer.isCoupangHost("m.coupang.com"));
        assertTrue(LinkUrlNormalizer.isCoupangHost("link.coupang.com"));
        assertTrue(LinkUrlNormalizer.isCoupangHost("coupa.ng"));
        assertFalse(LinkUrlNormalizer.isCoupangHost("evil-coupang.com"));
        assertFalse(LinkUrlNormalizer.isCoupangHost("coupang.com.evil.io"));
        assertFalse(LinkUrlNormalizer.isCoupangHost(null));
    }

    @Test
    void 형식_오류는_예외() {
        assertThrows(IllegalArgumentException.class, () -> LinkUrlNormalizer.parse(null));
        assertThrows(IllegalArgumentException.class, () -> LinkUrlNormalizer.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> LinkUrlNormalizer.parse("ftp://www.coupang.com/vp/products/1"));
        assertThrows(IllegalArgumentException.class, () -> LinkUrlNormalizer.parse("http://"));
        assertThrows(IllegalArgumentException.class, () -> LinkUrlNormalizer.parse("https://exa mple.com/x"));
        assertThrows(IllegalArgumentException.class, () -> LinkUrlNormalizer.parse("https://a.com/" + "x".repeat(2100)));
    }
}
