package com.example.nutriuniv.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 기능명세서 6.1·6.2 — 저장할 때와 찾을 때 같은 정규화. 「프로틴 바」=「프로틴바」, 「ㄷㄷㅂ」→더단백. */
class SearchNormalizerTest {

    @Test
    void 공백_특수문자_제거_소문자() {
        assertEquals("매일더단백", SearchNormalizer.normalize("매일 더:단백"));
        assertEquals("프로틴바", SearchNormalizer.normalize("프로틴 바"));
        assertEquals("proteinbar2", SearchNormalizer.normalize("Protein-Bar 2!"));
        assertEquals("", SearchNormalizer.normalize(" ~!@# "));
        assertEquals("", SearchNormalizer.normalize(null));
    }

    @Test
    void 초성_변환() {
        assertEquals("ㅁㅇㄷㄷㅂ", SearchNormalizer.chosung("매일 더:단백"));
        assertEquals("ㄸㄱㄲㅎ", SearchNormalizer.chosung("딸기 꽃 향"));       // 쌍자음 초성
        assertEquals("ㄷㄷㅂ", SearchNormalizer.chosung("ㄷㄷㅂ"));              // 이미 초성이면 그대로
        assertEquals("ㅍㄹㅌtwo", SearchNormalizer.chosung("프로틴 Two"));     // 한글 이외 글자·숫자는 소문자로 그대로
    }

    @Test
    void 초성_질의_판정() {
        assertTrue(SearchNormalizer.isChosungQuery("ㄷㄷㅂ"));
        assertTrue(SearchNormalizer.isChosungQuery("ㄷ ㄷ ㅂ"));
        assertFalse(SearchNormalizer.isChosungQuery("더단백"));
        assertFalse(SearchNormalizer.isChosungQuery("ㄷ단백"));
        assertFalse(SearchNormalizer.isChosungQuery(""));
    }

    @Test
    void 인덱스_맵은_원문_위치를_가리킨다() {
        SearchNormalizer.Mapped m = SearchNormalizer.normalizeWithMap("매일 더:단백");
        assertEquals("매일더단백", m.text());
        assertArrayEquals(new int[]{0, 1, 3, 5, 6}, m.originalIndex());
    }

    @Test
    void 하이라이트_구간_그대로_포함_정규화_초성_순() {
        // 그대로 포함
        assertArrayEquals(new int[]{3, 6}, SearchNormalizer.matchRange("더:단", "더단", null, "매일 더:단백"));
        // 공백·특수문자 제거 대조: 「더단백」 → 원문 3~7
        assertArrayEquals(new int[]{3, 7}, SearchNormalizer.matchRange("더단백", "더단백", null, "매일 더:단백"));
        // 초성: 「ㄷㄷㅂ」 → 원문 3~7
        assertArrayEquals(new int[]{3, 7}, SearchNormalizer.matchRange("ㄷㄷㅂ", "ㄷㄷㅂ", "ㄷㄷㅂ", "매일 더:단백"));
        // 없음
        assertNull(SearchNormalizer.matchRange("우유", "우유", null, "매일 더:단백"));
    }

    @Test
    void LIKE_와일드카드_이스케이프() {
        assertEquals("100\\%", SearchNormalizer.escapeLike("100%"));
        assertEquals("a\\_b\\\\c", SearchNormalizer.escapeLike("a_b\\c"));
    }
}
