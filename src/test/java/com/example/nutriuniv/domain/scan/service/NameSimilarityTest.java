package com.example.nutriuniv.domain.scan.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** SCAN_010 비슷한 후보 — 용량 표기를 뺀 이름의 Dice 0.9 이상. */
class NameSimilarityTest {

    @Test
    void 용량_개수_기호_띄어쓰기를_뺀다() {
        assertEquals("허니버터칩", NameSimilarity.normalizeName("허니버터칩 60g"));
        assertEquals("허니버터칩", NameSimilarity.normalizeName("허니 버터칩(120g x 2개)"));
        assertEquals("제로콜라", NameSimilarity.normalizeName("제로 콜라 355mL*24캔"));
    }

    @Test
    void 용량만_다르면_같은_이름() {
        assertEquals(1.0, NameSimilarity.similarity("허니버터칩 60g", "허니버터칩 120g"));
    }

    @Test
    void 맛이_다르면_후보가_아니다() {
        assertTrue(NameSimilarity.similarity("허니버터칩 60g", "허니버터칩 매운맛 60g") < NameSimilarity.THRESHOLD);
    }

    @Test
    void 회사_이름은_법인_표기를_뗀다() {
        assertEquals(NameSimilarity.normalizeCompany("해태제과식품"), NameSimilarity.normalizeCompany("해태제과식품(주)"));
        assertEquals(NameSimilarity.normalizeCompany("오리온"), NameSimilarity.normalizeCompany("주식회사 오리온 제2공장"));
    }
}
