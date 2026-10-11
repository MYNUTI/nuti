package com.example.nutriuniv.domain.ranking.service;

/**
 * 분류를 여는 조건 (기능명세서 6.3) — 둘 다 만족해야 한다.
 * ① 분석 완료 300건 이상 ② A와 D가 각각 1건 이상 (일반 기준 등급).
 * 순수 함수. 데이터 현황 대시보드(10.5)의 「게이트 통과 여부」도 이 판정을 쓴다.
 */
public final class RankingGate {

    public static final int MIN_ANALYZED = 300;

    private RankingGate() {}

    public static boolean passes(long analyzedCount, long aCount, long dCount) {
        return analyzedCount >= MIN_ANALYZED && aCount >= 1 && dCount >= 1;
    }

    /** 미통과 이유 한 줄 (관리자 화면용). 통과면 null. */
    public static String failureReason(long analyzedCount, long aCount, long dCount) {
        if (passes(analyzedCount, aCount, dCount)) return null;
        StringBuilder sb = new StringBuilder();
        if (analyzedCount < MIN_ANALYZED) {
            sb.append("분석 완료 ").append(analyzedCount).append("건 (").append(MIN_ANALYZED).append("건 필요)");
        }
        if (aCount < 1) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("A등급 없음");
        }
        if (dCount < 1) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("D등급 없음");
        }
        return sb.toString();
    }
}
