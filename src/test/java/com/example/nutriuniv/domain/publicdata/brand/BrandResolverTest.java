package com.example.nutriuniv.domain.publicdata.brand;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * brand 계산 — brand.md 「검증 예시」 15개(마스터 실제 행, 갈래마다 하나)를 실제 조회표로 고정한다.
 * 11-12 고침표는 아직 통합 마스터에서 뽑지 않아 테스트 전용 파일(resources/publicdata/brand-fixture/)에 두 줄만 넣었다.
 * 바코드 주인은 원래 C005 로 정하는 값이라, 그 갈래를 지나는 예시만 손으로 넣었다.
 */
class BrandResolverTest {

    private static BrandResolver resolver;

    @BeforeAll
    static void setUp() {
        resolver = new BrandResolver(new BrandLookupTables("publicdata/brand/", "publicdata/brand-fixture/"));
    }

    @ParameterizedTest(name = "{1} → {5} ({6})")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            # 묶음 키        | 제품명                          | 판매원             | 제조사                          | 바코드 주인  | 정답           | 지나는 갈래
            01ea2c05fd91608c | 오빠닭 닭가슴살 비엔나 오리지널 | -                  | (주)오뗄 포천용정지점           | -            | 오빠닭         | 사전
            0f1fabb7de5b1c44 | 스팸 김밥햄                     | 씨제이제일제당(주) | 주식회사 하이푸드텍             | -            | 스팸           | 하위 브랜드(SUB)
            fddacd062aba46be | 씨솔트 헤비브라운               | 파파오가닉         | 주식회사 케익모아               | -            | 파파오가닉     | 판매원
            1a644d1a41493f2e | 쥬시쿨복숭아                    | -                  | ㈜빙그레                        | -            | 빙그레         | 브랜드(10-28 웹 판정)
            9adf34c22f57a6f1 | 이천쌀호빵                      | -                  | ㈜에스피씨삼립                  | -            | 삼립           | merge1 SPC삼립 → merge2 삼립
            7d177bfe40941408 | 당근케이크                      | -                  | (주)신세계푸드                  | 신세계푸드   | 신세계푸드     | 둘 다 · 자기 제품
            41df731033e76599 | 월리 레드벨벳 치즈 케이크       | -                  | 주식회사신세계푸드천안공장      | 이마트       | -              | 둘 다 · 자기 제품 아님
            4465e400304351e7 | 혜자)제육김치볶음밥             | -                  | (주)후레쉬퍼스트                | -            | -              | 제조 전문
            ed520e25cccf2601 | 오복순진간장                    | -                  | (주)오복식품                    | -            | -              | 정한 곳이 규칙
            c3d2ad4688f573f7 | 겉절이                          | -                  | (주)이씨네식품                  | -            | -              | 이름표에 없음(신호 없음)
            7f012b25e4333f1b | 아몬드크로와상                  | -                  | 페이브베이커리                  | 다른회사식품 | -              | 바코드 주인이 다른 회사
            057d4cae8c6ef842 | 켈슨 데니쉬버터쿠키 454G        | -                  | KELSENGROUPA/S                  | -            | -              | clean(해외 꼬리)
            623c387788362fa6 | 명품완도김어묵(냉동)            | -                  | 주식회사케이제이지푸드          | -            | 김종구부산어묵 | 쿠팡 보정
            b1b59fb6de275ae3 | 빼빼로더블딥딸기화이트          | -                  | 롯데제과주식회사                | -            | 롯데           | 11-12 수동 고침
            4f80879f80305f34 | 돼지고기호박짜글이 세트(W)      | -                  | (주)현대그린푸드 스마트푸드센터 | -            | -              | 11-12 수동 비움
            """)
    void brand_md_검증_예시(String groupKey, String name, String seller, String maker, String owner, String expected, String branch) {
        assertEquals(expected, resolver.resolve(new BrandResolver.Input(groupKey, name, seller, maker, null, owner)), branch);
    }

    @Test
    void 메뉴_머리말이면_사전을_건너뛴다() {
        // 「오빠닭」은 사전에 있지만 「오빠닭_ …」은 메뉴 머리말 — 사전을 건너뛰고 회사 이름으로 간다(여기선 표에 없어 null)
        assertEquals("오빠닭", resolver.resolve(input("오빠닭 세트", null, "표에없는회사")));
        assertNull(resolver.resolve(input("오빠닭_ 세트", null, "표에없는회사")));
    }

    @Test
    void 하위_브랜드는_그_회사_제품일_때만() {
        // 맛밤 · 맥심은 사전에 없어 SUB 갈래로 온다
        assertEquals("맛밤", resolver.resolve(input("맛밤 군밤", "씨제이제일제당(주)", null)));
        assertEquals("맥심", resolver.resolve(input("맥심 모카골드", null, "동서식품(주)")));
        assertNull(resolver.resolve(input("맛밤 군밤", null, "(주)이씨네식품")));   // 다른 회사면 하위 브랜드 아님(이씨네식품은 이름표 미확인 → null)
    }

    @Test
    void 회사_이름이_하나도_없으면_null() {
        assertNull(resolver.resolve(input("이름만있는제품", null, null)));
    }

    @Test
    void 바코드_주인이_계열사면_남의_제품으로_보지_않는다() {
        // 해태아이스크림 제품의 바코드 주인이 빙그레 — 계열 5쌍
        assertEquals("해태아이스크림", resolver.resolve(
                new BrandResolver.Input("0000000000000000", "부라보콘", null, "해태아이스크림(주)", null, "빙그레")));
        assertNull(resolver.resolve(
                new BrandResolver.Input("0000000000000000", "부라보콘", null, "해태아이스크림(주)", null, "다른회사식품")));
    }

    @Test
    void 조회표가_준비되지_않았으면_계산하지_않는다() {
        BrandResolver notReady = new BrandResolver(new BrandLookupTables("없는/폴더/"));
        assertFalse(notReady.isReady());
        assertThrows(IllegalStateException.class, () -> notReady.resolve(input("오빠닭", null, null)));
    }

    private static BrandResolver.Input input(String name, String seller, String maker) {
        return new BrandResolver.Input("0000000000000000", name, seller, maker, null, null);
    }
}