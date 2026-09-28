package com.example.nutriuniv.domain.search.util;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchHighlightTest {

    @Test
    void 목표_기준_위반_문구() {
        assertEquals("당류 21g — 체중 감량 기준 초과",
                SearchHighlight.build(GoalType.WEIGHT_LOSS, Nutrient.SUGAR, new BigDecimal("21.000")));
        assertEquals("나트륨 1000mg — 기준 초과",
                SearchHighlight.build(GoalType.GENERAL, Nutrient.SODIUM, new BigDecimal("1000")));
        assertEquals("포화지방 1.5g — 근육 증가 기준 초과",
                SearchHighlight.build(GoalType.MUSCLE_GAIN, Nutrient.SATURATED_FAT, new BigDecimal("1.50")));
    }

    @Test
    void 감점_요인이_없으면_null() {
        assertNull(SearchHighlight.build(GoalType.WEIGHT_LOSS, null, BigDecimal.ONE));
        assertNull(SearchHighlight.build(GoalType.WEIGHT_LOSS, Nutrient.SUGAR, null));
    }
}
