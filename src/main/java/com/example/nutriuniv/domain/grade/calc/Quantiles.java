package com.example.nutriuniv.domain.grade.calc;

/** 분위수 — 정렬된 배열에 선형 보간(numpy/pandas 기본 방식). 재산출(4.2)의 P1/P99 앵커와 80/60/40/20% 컷오프에 쓴다. */
public final class Quantiles {

    private Quantiles() {}

    /**
     * @param sortedAsc 오름차순 정렬된 값 (비어 있으면 예외)
     * @param q         0.0 ~ 1.0
     */
    public static double linear(double[] sortedAsc, double q) {
        if (sortedAsc == null || sortedAsc.length == 0) {
            throw new IllegalArgumentException("분위수를 구할 값이 없습니다.");
        }
        if (q < 0.0 || q > 1.0) {
            throw new IllegalArgumentException("q 는 0~1 사이여야 합니다: " + q);
        }
        int n = sortedAsc.length;
        if (n == 1) return sortedAsc[0];

        double pos = q * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sortedAsc[lo];
        double frac = pos - lo;
        return sortedAsc[lo] + (sortedAsc[hi] - sortedAsc[lo]) * frac;
    }
}
