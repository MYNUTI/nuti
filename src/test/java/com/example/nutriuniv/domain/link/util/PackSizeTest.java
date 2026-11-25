package com.example.nutriuniv.domain.link.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 상품명의 1개 용량 · 개수 (DATA_006 · 11-24) — 코드 규칙으로만 읽는다. */
class PackSizeTest {

    @Test
    void 용량과_개수를_읽고_kg_L_은_g_mL_로_맞춘다() {
        assertSize("곰곰 닭가슴살, 100g, 10개", PackSize.Unit.G, "100", 10);
        assertSize("햇반 210g x 12개", PackSize.Unit.G, "210", 12);
        assertSize("햇반 210gx12", PackSize.Unit.G, "210", 12);
        assertSize("제주 삼다수 2L 6병", PackSize.Unit.ML, "2000", 6);
        assertSize("서울우유 1.8L, 1개", PackSize.Unit.ML, "1800", 1);
        assertSize("닭가슴살 1.2kg 1팩", PackSize.Unit.G, "1200", 1);
        assertSize("두유 190ml 24팩", PackSize.Unit.ML, "190", 24);
        assertSize("견과 25g 30개입", PackSize.Unit.G, "25", 30);
        assertSize("쌀 1,000g 1개", PackSize.Unit.G, "1000", 1);   // 천 단위 쉼표
    }

    @Test
    void 없거나_여러_가지로_적힌_값은_읽지_않는다() {
        PackSize none = PackSize.parse("곰곰 통밀 식빵");
        assertNull(none.amount());
        assertNull(none.count());
        assertFalse(none.complete());

        PackSize noCount = PackSize.parse("곰곰 통밀 식빵 700g");
        assertEquals(0, new BigDecimal("700").compareTo(noCount.amount()));
        assertNull(noCount.count());
        assertFalse(noCount.complete());

        assertNull(PackSize.parse("닭가슴살 100g + 120g 2개").amount());   // 용량이 둘
        assertNull(PackSize.parse("닭가슴살 100g 2개, 3팩").count());       // 개수가 둘
        assertNull(PackSize.parse("우유 200ml 2개 + 과자 30g").unit());     // g 와 mL 가 섞임
        assertNull(PackSize.parse("비타민 500mg 60정").amount());          // mg 은 성분 표기
    }

    @Test
    void 양쪽에_다_적혀_있을_때만_같은_용량_다른_용량() {
        assertEquals(PackSize.Match.SAME, PackSize.compare("닭가슴살 100g 1개", "닭가슴살, 100g, 1개"));
        assertEquals(PackSize.Match.SAME, PackSize.compare("우유 1L 1개", "우유, 1000ml, 1개"));
        assertEquals(PackSize.Match.OTHER, PackSize.compare("닭가슴살 100g 1개", "닭가슴살, 100g, 10개"));
        assertEquals(PackSize.Match.OTHER, PackSize.compare("닭가슴살 100g 1개", "닭가슴살, 120g, 1개"));
        assertEquals(PackSize.Match.OTHER, PackSize.compare("음료 100g 1개", "음료, 100ml, 1개"));   // g 와 mL 는 다르다
        assertEquals(PackSize.Match.UNKNOWN, PackSize.compare("닭가슴살", "닭가슴살, 100g, 1개"));
        assertEquals(PackSize.Match.UNKNOWN, PackSize.compare("닭가슴살 100g", "닭가슴살, 100g, 1개"));   // 우리 쪽 개수 없음 — 1개로 보지 않는다
        assertEquals(PackSize.Match.UNKNOWN, PackSize.compare("닭가슴살 100g 1개", "닭가슴살 100g"));
    }

    @Test
    void 백g당과_1개당_계산식() {
        PackSize s = PackSize.parse("닭가슴살 100g 10개");
        assertEquals(2150, s.per100(21500));     // 21,500 ÷ (100 × 10) × 100
        assertEquals(2150, s.perUnit(21500));    // 21,500 ÷ 10
        PackSize ml = PackSize.parse("두유 190ml 24팩");
        assertEquals(263, ml.per100(12000));     // 12,000 ÷ 4,560 × 100 = 263.2 → 263
        assertEquals(500, ml.perUnit(12000));
        assertNull(PackSize.parse("식빵 700g").per100(3500));    // 개수가 없으면 내지 않는다
        assertNull(PackSize.parse("식빵 700g").perUnit(3500));
        assertNull(s.per100(null));
    }

    private static void assertSize(String name, PackSize.Unit unit, String amount, int count) {
        PackSize p = PackSize.parse(name);
        assertEquals(unit, p.unit(), name);
        assertEquals(0, new BigDecimal(amount).compareTo(p.amount()), name);
        assertEquals(count, p.count(), name);
        assertTrue(p.complete(), name);
    }
}
