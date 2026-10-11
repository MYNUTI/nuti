package com.example.nutriuniv.common.util;

import java.util.Arrays;

/**
 * 검색 색인·질의 정규화 (기능명세서 6.1·6.2). 저장할 때(products.name_normalized·name_chosung)와 찾을 때 같은 함수를 쓴다.
 * <ul>
 *   <li>normalize — 소문자화 + 공백·특수문자 제거 (글자·숫자만 남김): 「매일 더:단백」 → 「매일더단백」</li>
 *   <li>chosung — 한글 음절은 초성으로, 그 외 글자·숫자는 소문자 그대로, 공백·특수문자 제거: 「매일 더:단백」 → 「ㅁㅇㄷㄷㅂ」</li>
 *   <li>…WithMap — 정규화 문자열의 각 글자가 원문의 몇 번째 글자인지(하이라이트 구간 복원용)</li>
 * </ul>
 * 순수 함수. 테스트(SearchNormalizerTest)로 고정.
 */
public final class SearchNormalizer {

    private static final char[] CHOSEONG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };
    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_LAST = 0xD7A3;
    private static final char JAMO_FIRST = 0x3131;   // ㄱ
    private static final char JAMO_LAST  = 0x314E;   // ㅎ

    private SearchNormalizer() {}

    /** 정규화 결과와 원문 인덱스 맵. originalIndex[i] = text 의 i 번째 글자가 원문에서 있던 위치. */
    public record Mapped(String text, int[] originalIndex) {}

    public static String normalize(String s) {
        return normalizeWithMap(s).text();
    }

    public static Mapped normalizeWithMap(String s) {
        if (s == null) return new Mapped("", new int[0]);
        StringBuilder sb = new StringBuilder(s.length());
        int[] idx = new int[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
                idx[n++] = i;
            }
        }
        return new Mapped(sb.toString(), Arrays.copyOf(idx, n));
    }

    public static String chosung(String s) {
        return chosungWithMap(s).text();
    }

    public static Mapped chosungWithMap(String s) {
        if (s == null) return new Mapped("", new int[0]);
        StringBuilder sb = new StringBuilder(s.length());
        int[] idx = new int[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= HANGUL_BASE && c <= HANGUL_LAST) {
                sb.append(CHOSEONG[(c - HANGUL_BASE) / 588]);
                idx[n++] = i;
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
                idx[n++] = i;
            }
        }
        return new Mapped(sb.toString(), Arrays.copyOf(idx, n));
    }

    /** 질의가 초성만으로 이루어졌는가 (「ㄷㄷㅂ」). 공백·특수문자는 무시. 비어 있으면 false. */
    public static boolean isChosungQuery(String s) {
        String t = normalize(s);
        if (t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < JAMO_FIRST || c > JAMO_LAST) return false;
        }
        return true;
    }

    /** LIKE 패턴에 넣기 전 와일드카드 이스케이프 (PostgreSQL 기본 이스케이프 문자 = 역슬래시). */
    public static String escapeLike(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 하이라이트 구간 [start, end) — 원문 이름에서 질의가 매치된 위치. 순서: 그대로 포함 → 공백·특수문자 제거 대조 → 초성.
     * 어디에도 없으면 null (유사도·동의어로 잡힌 결과).
     */
    public static int[] matchRange(String rawQuery, String normalizedQuery, String chosungQuery, String name) {
        if (name == null || name.isEmpty()) return null;
        if (rawQuery != null && !rawQuery.isBlank()) {
            int i = name.toLowerCase().indexOf(rawQuery.trim().toLowerCase());
            if (i >= 0) return new int[]{i, i + rawQuery.trim().length()};
        }
        if (normalizedQuery != null && !normalizedQuery.isEmpty()) {
            Mapped m = normalizeWithMap(name);
            int j = m.text().indexOf(normalizedQuery);
            if (j >= 0) return range(m, j, normalizedQuery.length());
        }
        if (chosungQuery != null && !chosungQuery.isEmpty()) {
            Mapped m = chosungWithMap(name);
            int k = m.text().indexOf(chosungQuery);
            if (k >= 0) return range(m, k, chosungQuery.length());
        }
        return null;
    }

    private static int[] range(Mapped m, int start, int len) {
        return new int[]{m.originalIndex()[start], m.originalIndex()[start + len - 1] + 1};
    }
}
