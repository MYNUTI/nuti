package com.example.nutriuniv.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditDistanceTest {

    @Test
    void 레벤슈타인() {
        assertEquals(0, EditDistance.levenshtein("프로틴바", "프로틴바"));
        assertEquals(1, EditDistance.levenshtein("프로틴바", "프로틴빠"));
        assertEquals(1, EditDistance.levenshtein("abc", "abd"));
        assertEquals(2, EditDistance.levenshtein("", "ab"));
        assertEquals(3, EditDistance.levenshtein("kitten", "sitten") + EditDistance.levenshtein("sittin", "sitting") + 1);
        assertEquals(0, EditDistance.levenshtein(null, null));
    }

    @Test
    void within은_길이_차이로_먼저_걸러낸다() {
        assertTrue(EditDistance.within("프로틴바", "프로틴빠", 2));
        assertFalse(EditDistance.within("프로틴바", "프로틴바초코맛", 2));
        assertFalse(EditDistance.within("abc", "xyz", 2));
    }
}
