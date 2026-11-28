package com.example.nutriuniv.domain.scan.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 「비슷한 후보」 이름 비교 (v3 SCAN_010 · 11-20) — 용량 · 개수 표기와 기호 · 띄어쓰기를 뺀 이름끼리 글자 두 개씩 겹치는 정도(Dice).
 * 0.9 이상이면 후보로 본다.
 */
public final class NameSimilarity {

    public static final double THRESHOLD = 0.9;

    private static final Pattern AMOUNT = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*(?:kg|mg|g|ml|l|kcal|개입|개|입|봉지|봉|팩|병|캔|ea|매|정|포|구|p)(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMES = Pattern.compile("[x×*]\\s*\\d+\\s*(?:개입|개|입|봉지|봉|팩|병|캔|ea|p)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern COMPANY_NOISE = Pattern.compile(
            "\\(주\\)|㈜|주식회사|\\(유\\)|유한회사|농업회사법인|영농조합법인|\\(합\\)|합자회사|제\\d*공장|공장", Pattern.CASE_INSENSITIVE);

    private NameSimilarity() {}

    /** 용량 · 개수 표기, 기호, 띄어쓰기를 뺀 소문자 이름. */
    public static String normalizeName(String name) {
        if (name == null) return "";
        String s = name.toLowerCase(Locale.ROOT);
        s = TIMES.matcher(s).replaceAll(" ");      // 「x 2개」를 먼저 — 개수만 빠지고 x 가 남지 않게
        s = AMOUNT.matcher(s).replaceAll(" ");
        return NON_WORD.matcher(s).replaceAll("");
    }

    /** 회사 이름 — 법인 · 공장 표기와 기호 · 띄어쓰기를 뗀다. */
    public static String normalizeCompany(String company) {
        if (company == null) return "";
        String s = COMPANY_NOISE.matcher(company).replaceAll(" ");
        return NON_WORD.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** 두 이름(이미 normalizeName 한 값)의 Dice 계수 — 글자 두 개 묶음 기준. 한 글자 이름은 같을 때만 1. */
    public static double dice(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.length() < 2 || b.length() < 2) return 0.0;
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        int overlap = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer n = grams.get(g);
            if (n != null && n > 0) {
                overlap++;
                grams.put(g, n - 1);
            }
        }
        return 2.0 * overlap / ((a.length() - 1) + (b.length() - 1));
    }

    public static double similarity(String nameA, String nameB) {
        return dice(normalizeName(nameA), normalizeName(nameB));
    }
}
