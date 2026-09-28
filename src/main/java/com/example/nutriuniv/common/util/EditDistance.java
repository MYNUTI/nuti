package com.example.nutriuniv.common.util;

/** 레벤슈타인 편집거리 — 오타 교정 제안(didYouMean)은 편집거리 ≤ 2 일 때만 (기능명세서 6.1·6.2). */
public final class EditDistance {

    private EditDistance() {}

    public static int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }

    /** 길이 차이만으로 max 를 넘으면 계산 없이 false. */
    public static boolean within(String a, String b, int max) {
        int la = a == null ? 0 : a.length();
        int lb = b == null ? 0 : b.length();
        if (Math.abs(la - lb) > max) return false;
        return levenshtein(a, b) <= max;
    }
}
