package com.example.nutriuniv.domain.ranking.service;

/**
 * 랭킹 공개 기준 (v3 SRCH_007 · SRCH_006) — 분석 완료 300개 이상인 칩(중분류 · 합산 칩)만 순위를 연다.
 * v2 의 「A · D 각 1건 이상」 조건은 없앴다.
 */
public final class RankingGate {

    public static final int MIN_ANALYZED = 300;

    private RankingGate() {}

    public static boolean passes(long analyzedCount) {
        return analyzedCount >= MIN_ANALYZED;
    }

    /** 미통과 이유 한 줄 (관리자 화면용). 통과면 null. */
    public static String failureReason(long analyzedCount) {
        return passes(analyzedCount) ? null : "분석 완료 " + analyzedCount + "건 (" + MIN_ANALYZED + "건 필요)";
    }
}
