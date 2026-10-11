package com.example.nutriuniv.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;

/** GET /admin/metrics/retention 출력 (기능명세서 9.2) — 2차 변경은 모수 정의 하나(동의한 사용자)와 그 변경 시점 기록. */
@Getter
@Builder
public class RetentionMetricsResponse {

    private long baseCount;                 // 0~6일차 유효 방문 · 동의한 사용자
    private long revisitCount;              // 그중 7~27일차 유효 재방문
    private double rate;                    // revisitCount / baseCount (모수 0 이면 0)
    private String verdict;                 // PASS(20%↑) | HOLD(7~20%) | FAIL(7%↓)
    private Definition definition;

    @Getter
    @Builder
    public static class Definition {
        private int sessionTimeoutMin;      // 30
        private String validVisit;          // 검색·제품 조회 1회 이상 (클릭·필터 제외)
        private String window;              // 0~6일차 모수 · 7~27일차 재방문 · 날짜 경계 KST
        private String population;          // 동의한 사용자(익명 ID 발급자)
        private String changedAt;           // 모수 정의를 바꾼 시점
    }
}
