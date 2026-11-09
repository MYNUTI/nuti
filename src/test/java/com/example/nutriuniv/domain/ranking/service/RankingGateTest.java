package com.example.nutriuniv.domain.ranking.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RankingGateTest {

    @Test
    void 분석완료_300개부터_연다() {
        assertTrue(RankingGate.passes(300));
        assertFalse(RankingGate.passes(299));
        assertNull(RankingGate.failureReason(300));
        assertEquals("분석 완료 120건 (300건 필요)", RankingGate.failureReason(120));
    }
}
