package com.example.nutriuniv.domain.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 기능명세서 9.2 판정 — 20% 이상 통과 / 7~20% 보류 / 7% 미만 실패. */
class AdminMetricsServiceTest {

    @Test
    void 판정_경계() {
        assertEquals("PASS", AdminMetricsService.verdict(0.20));
        assertEquals("PASS", AdminMetricsService.verdict(0.35));
        assertEquals("HOLD", AdminMetricsService.verdict(0.199));
        assertEquals("HOLD", AdminMetricsService.verdict(0.07));
        assertEquals("FAIL", AdminMetricsService.verdict(0.069));
        assertEquals("FAIL", AdminMetricsService.verdict(0.0));
    }
}
