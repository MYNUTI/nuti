package com.example.nutriuniv.domain.publicdata.brand;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 조회표 파일(resources/publicdata/brand/)을 읽어 건수와 대표 값을 고정한다. 표를 바꾸면 건수도 같이 고친다. */
class BrandLookupTablesTest {

    private static BrandLookupTables tables;

    @BeforeAll
    static void load() {
        tables = new BrandLookupTables();
    }

    @Test
    void 필수_파일이_없어도_예외_없이_만들어지고_준비_안_됨으로_표시된다() {
        BrandLookupTables missing = new BrandLookupTables("없는/폴더/");

        assertFalse(missing.isReady());
        assertTrue(missing.problem().contains("brand_dictionary.csv"));
        assertEquals(0, missing.dictionarySize());
        assertEquals(0, missing.companyLabelSize());
        assertEquals(Optional.empty(), missing.dictionaryLookup("하림"));
    }

    @Test
    void 파일을_모두_읽는다() {
        assertTrue(tables.isReady());
        assertNull(tables.problem());
        assertEquals(1407, tables.dictionarySize());     // 1_브랜드사전_2026-11-09
        assertEquals(29546, tables.companyLabelSize());  // 2_회사이름표_2026-10-29 (전체)
        assertEquals(61, tables.merge2Size());           // 3_같은회사_합치기표_2026-10-29 (28묶음)
        assertEquals(1059, tables.coupangFixSize());     // 4_쿠팡보정_고정값_2026-11-09
    }

    @Test
    void 선택_표가_없으면_빈_표() {
        // 통합 마스터에서 뽑을 표 — 파일을 넣기 전에는 비어 있다
        assertEquals(0, tables.fix1112Size());
        assertEquals(0, tables.coupangNameSize());
        assertEquals(Optional.empty(), tables.fix1112("b1b59fb6de275ae3"));
        assertEquals("", tables.coupangName("b1b59fb6de275ae3"));
    }

    @Test
    void 사전은_그대로_있으면_그_브랜드() {
        assertEquals(Optional.of("오빠닭"), tables.dictionaryLookup("오빠닭"));
        assertEquals(Optional.of("하림"), tables.dictionaryLookup("하림"));
    }

    @Test
    void 붙여_써도_쓰는_낱말은_앞부분만_맞아도_인정() {
        assertEquals(Optional.of("하림"), tables.dictionaryLookup("하림프로틴쿠키"));   // brand.md 예시
    }

    @Test
    void 붙여쓰기_N_낱말은_앞부분으로_인정하지_않는다() {
        // 「롯데」는 사전에 있지만 붙여 쓴 이름에도 씀 = N
        assertEquals(Optional.of("롯데"), tables.dictionaryLookup("롯데"));
        assertEquals(Optional.empty(), tables.dictionaryLookup("롯데샌드"));
    }

    @Test
    void 빈_낱말은_없음() {
        assertEquals(Optional.empty(), tables.dictionaryLookup(""));
        assertEquals(Optional.empty(), tables.dictionaryLookup(null));
    }

    @Test
    void 회사_이름표와_믿을_만한_판정() {
        BrandLookupTables.CompanyLabel bingrae = tables.companyLabel("빙그레").orElseThrow();
        assertEquals(BrandLookupTables.LABEL_BRAND, bingrae.label());
        assertTrue(bingrae.trusted());                                   // 10-28 웹 판정

        assertEquals(BrandLookupTables.LABEL_BOTH, tables.companyLabel("신세계푸드").orElseThrow().label());
        assertFalse(tables.companyLabel("오복식품").orElseThrow().trusted());   // 브랜드지만 정한 곳이 「규칙」
        assertEquals("제조 전문", tables.companyLabel("후레쉬퍼스트").orElseThrow().label());
        assertEquals(Optional.empty(), tables.companyLabel("표에없는새회사"));
    }

    @Test
    void 합치기는_정확히_같은_이름만() {
        assertEquals("삼립", tables.merge2("SPC삼립"));
        assertEquals("CJ", tables.merge2("CJ제일제당"));
        assertEquals("동원", tables.merge2("동원F&B"));
        assertEquals("해태htb", tables.merge2("해태htb"));               // 다른 회사라 일부러 뺌
        assertEquals("매일식품", tables.merge2("매일식품"));
        assertNull(tables.merge2(null));
    }

    @Test
    void 쿠팡_보정은_묶음_키로() {
        assertEquals(Optional.of("김종구부산어묵"), tables.coupangFix("623c387788362fa6"));   // brand.md 검증 예시
        assertEquals(Optional.empty(), tables.coupangFix("0000000000000000"));
    }
}