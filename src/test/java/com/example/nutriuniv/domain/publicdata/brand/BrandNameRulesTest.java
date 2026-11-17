package com.example.nutriuniv.domain.publicdata.brand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** brand.md 「함수 정의」 · 「회사 이름 정리」 — 조회표 없이 문자열 규칙만. */
class BrandNameRulesTest {

    /** 시도 + 예시에 쓰는 시 · 군 지명. 실제로는 I0300 · I1420 SITE_ADDR 에서 뽑아 더한다. */
    private final BrandNameRules rules = new BrandNameRules(Set.of("서울", "경기", "원주", "천안", "익산", "서천"));

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            (주)빙그레                         | 빙그레
            ㈜에스피씨삼립                     | 에스피씨삼립
            롯데제과주식회사                   | 롯데제과
            농업회사법인 주식회사 코리아식품   | 코리아식품
            주식회사신세계푸드천안공장         | 신세계푸드
            (주)현대그린푸드 스마트푸드센터    | 현대그린푸드
            (주)오뗄 포천용정지점              | 오뗄
            (주)이연에프엔씨 오송공장          | 이연에프엔씨
            크리스피크림원주무실점             | 크리스피크림
            우양서천공장                       | 우양
            제이앤제이 제2공장                 | 제이앤제이
            (주)내담(NAEDAM F&B)               | 내담
            씨제이제일제당(주)진천 BLOSSOM CAMPUS | 씨제이제일제당 진천 BLOSSOM CAMPUS
            MORINAGA & CO.,LTD. OYAMA FACTORY  | MORINAGA
            MILUPAGMBH                         | MILUPA
            NESTLE S.A.                        | NESTLE
            KELSENGROUPA/S                     | KELSENGROUP
            M/S ABC FOODS                      | ABC FOODS
            가나식품/다라유통                  | 가나식품
            """)
    void 회사_이름_정리(String raw, String expected) {
        assertEquals(expected, rules.clean(raw));
    }

    @Test
    void 정리하고_비면_원문() {
        assertEquals("주식회사", rules.clean("주식회사"));
    }

    @Test
    void 지명_목록에_없는_지명은_떼지_않는다() {
        BrandNameRules provincesOnly = new BrandNameRules();   // 시도 17개만
        assertEquals("신세계푸드천안", provincesOnly.clean("주식회사신세계푸드천안공장"));   // 끝의 「공장」만 뗌
        assertEquals("크리스피크림원주무실점", provincesOnly.clean("크리스피크림원주무실점"));
    }

    @Test
    void 해외_법인_꼬리_2차() {
        assertEquals("INDOFOOD SUKSES MAKMUR", BrandNameRules.stripForeign("PT. INDOFOOD SUKSES MAKMUR TBK"));
        assertEquals("BARILLA G. E R. FRATELLI", BrandNameRules.stripForeign("BARILLA G. E R. FRATELLI SPA"));
        assertEquals("JOHNSON & CO", BrandNameRules.stripForeign("JOHNSON & CO"));       // 「& CO」는 남김
        assertEquals("ABC", BrandNameRules.stripForeign("ABC CO"));                     // 맨 끝 CO 는 뗌
        assertEquals("AB", BrandNameRules.stripForeign("AB"));
    }

    @Test
    void compact는_소문자_한글_영숫자_앤드_플러스만() {
        assertEquals("동원f&b", BrandNameRules.compact("동원F&B"));
        assertEquals("혜자제육김치볶음밥", BrandNameRules.compact("혜자)제육김치볶음밥"));
        assertEquals("", BrandNameRules.compact(null));
    }

    @Test
    void 낱말_나누기() {
        assertEquals(List.of("피코크", "초코칩", "쿠키", "_", "파티"), BrandNameRules.tokens("[피코크]초코칩 쿠키_파티"));
        assertEquals(List.of("피자", "_", "콤비네이션"), BrandNameRules.tokens("피자_ 콤비네이션"));
        assertEquals(List.of("A", "B", "C", "D"), BrandNameRules.tokens("A/B·C,D"));
        assertEquals(List.of(), BrandNameRules.tokens(null));
    }

    @Test
    void 일단계_합치기() {
        assertEquals("CJ제일제당", BrandNameRules.merge1("씨제이제일제당"));
        assertEquals("롯데웰푸드", BrandNameRules.merge1("롯데제과"));
        assertEquals("SPC삼립", BrandNameRules.merge1("에스피씨삼립"));
        assertEquals("hy", BrandNameRules.merge1("한국야쿠르트"));
        assertEquals("빙그레", BrandNameRules.merge1("빙그레"));
        assertEquals("빙그레푸드", BrandNameRules.merge1("빙그레푸드"));   // 「같음」 방식은 정확히 같을 때만
    }

    @Test
    void 꼬리_떼기와_같은_회사() {
        assertEquals("신세계", BrandNameRules.core("신세계푸드"));
        assertEquals("오복", BrandNameRules.core("오복식품"));
        assertEquals("팜", BrandNameRules.core("팜"));                     // 떼면 2글자 미만 — 그대로
        assertTrue(BrandNameRules.same("남양유업", "남양"));
        assertTrue(BrandNameRules.same("신세계푸드", "신세계푸드"));
        assertFalse(BrandNameRules.same("신세계푸드", "이마트"));
        assertFalse(BrandNameRules.same(null, "이마트"));
    }

    @Test
    void 계열_5쌍() {
        assertTrue(BrandNameRules.affiliate("해태아이스크림", "빙그레"));
        assertTrue(BrandNameRules.affiliate("비락", "hy"));
        assertFalse(BrandNameRules.affiliate("빙그레", "해태아이스크림"));   // 방향이 있다(회사, 바코드 주인)
    }

    @Test
    void 제조_값은_null() {
        assertNull(BrandNameRules.nullIfMaker("제조 신세계푸드"));
        assertNull(BrandNameRules.nullIfMaker(""));
        assertNull(BrandNameRules.nullIfMaker(null));
        assertEquals("롯데", BrandNameRules.nullIfMaker("롯데"));
    }
}