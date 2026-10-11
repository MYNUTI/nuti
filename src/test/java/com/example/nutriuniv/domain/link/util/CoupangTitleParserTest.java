package com.example.nutriuniv.domain.link.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 링크 인식 — 쿠팡 페이지 제목 추출·제품명 정리. */
class CoupangTitleParserTest {

    @Test
    void og_title_우선_속성_순서_무관() {
        String a = "<html><head><title>쿠팡!</title><meta property=\"og:title\" content=\"곰곰 통밀 식빵, 700g, 1개 - 쿠팡!\"></head></html>";
        String b = "<html><head><meta content='매일 더:단백 초코, 250ml, 12개' property='og:title'/><title>x</title></head></html>";
        assertEquals("곰곰 통밀 식빵, 700g, 1개 - 쿠팡!", CoupangTitleParser.extractTitle(a).orElseThrow());
        assertEquals("매일 더:단백 초코, 250ml, 12개", CoupangTitleParser.extractTitle(b).orElseThrow());
    }

    @Test
    void og_title_없으면_title_태그_엔티티_해제() {
        String html = "<html><head><title>\n  단백질바 &amp; 초코 | 쿠팡  </title></head></html>";
        assertEquals("단백질바 & 초코 | 쿠팡", CoupangTitleParser.extractTitle(html).orElseThrow());
        assertTrue(CoupangTitleParser.extractTitle("<html></html>").isEmpty());
        assertTrue(CoupangTitleParser.extractTitle(null).isEmpty());
    }

    @Test
    void 사이트_접미와_수량_토막_제거() {
        assertEquals("곰곰 통밀 식빵", CoupangTitleParser.clean("곰곰 통밀 식빵, 700g, 1개 - 쿠팡!"));
        assertEquals("매일 더:단백 초코", CoupangTitleParser.clean("매일 더:단백 초코, 250ml, 12개"));
        assertEquals("단백질바 & 초코", CoupangTitleParser.clean("단백질바 & 초코 | 쿠팡"));
        assertEquals("오리온 초코파이", CoupangTitleParser.clean("오리온 초코파이, 39g x 12개, 2박스"));
        assertEquals("닭가슴살 스파이시", CoupangTitleParser.clean("닭가슴살 스파이시, 100g, 10개입"));
        assertEquals("", CoupangTitleParser.clean(null));
    }

    @Test
    void 전부_수량이면_원문_유지() {
        assertEquals("700g, 1개", CoupangTitleParser.clean("700g, 1개"));
    }
}
