package com.example.nutriuniv.domain.ranking.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 기능명세서 6.3 — 분류를 여는 조건: 분석 완료 300건 이상 AND A·D 각 1건 이상. */
class RankingGateTest {

    @Test
    void 경계값() {
        assertTrue(RankingGate.passes(300, 1, 1));
        assertFalse(RankingGate.passes(299, 50, 50));
        assertFalse(RankingGate.passes(10_000, 0, 500));      // 초콜릿처럼 A 가 없으면 닫힘
        assertFalse(RankingGate.passes(10_000, 3, 0));
        assertTrue(RankingGate.passes(10_077, 3, 2_000));      // 「1위인데 D」가 나오더라도 조건은 만족
    }

    @Test
    void 미통과_이유() {
        assertNull(RankingGate.failureReason(300, 1, 1));
        assertEquals("분석 완료 120건 (300건 필요)", RankingGate.failureReason(120, 5, 5));
        assertEquals("A등급 없음", RankingGate.failureReason(500, 0, 5));
        assertEquals("분석 완료 10건 (300건 필요) · A등급 없음 · D등급 없음", RankingGate.failureReason(10, 0, 0));
    }
}
