package com.example.nutriuniv.domain.analysis.entity;

import com.example.nutriuniv.common.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** v3 — 분석 중 → 확인 중 → 완료 / 분석 못 함. 옛 값도 새 값으로 받는다. */
class AnalysisRequestStatusTest {

    @Test
    void 옛_값을_새_값으로() {
        assertEquals(AnalysisRequestStatus.ANALYZING, AnalysisRequestStatus.from("WAITING"));
        assertEquals(AnalysisRequestStatus.REVIEWING, AnalysisRequestStatus.from(" processing "));
        assertEquals(AnalysisRequestStatus.FAILED, AnalysisRequestStatus.from("HOLD"));
        assertEquals(AnalysisRequestStatus.DONE, AnalysisRequestStatus.from("done"));
        assertThrows(CustomException.class, () -> AnalysisRequestStatus.from("PAUSED"));
    }

    @Test
    void 완료와_분석_못_함이_끝() {
        assertTrue(AnalysisRequestStatus.DONE.isTerminal());
        assertTrue(AnalysisRequestStatus.FAILED.isTerminal());
        assertFalse(AnalysisRequestStatus.REVIEWING.isTerminal());
        assertEquals("분석 못 함", AnalysisRequestStatus.FAILED.label());
    }

    @Test
    void 제품명_요청은_브랜드와_함께_보인다() {
        AnalysisRequest r = AnalysisRequest.nameRequest("바나나맛 우유", "빙그레", null, "바나나우유");
        assertEquals("NAME", r.kind());
        assertEquals("빙그레 바나나맛 우유", r.identifier());
        assertEquals(AnalysisRequestStatus.ANALYZING, r.getStatus());
        assertEquals(1, r.getRequestCount());
    }
}
