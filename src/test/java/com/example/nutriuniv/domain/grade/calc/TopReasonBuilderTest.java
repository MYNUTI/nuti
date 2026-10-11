package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.grade.entity.Nutrient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TopReasonBuilderTest {

    @Test
    void 제공량_값과_기준치가_있으면_하루_기준치_비율_문장() {
        // 당류 42g / 1일 기준치 100g = 42%
        assertEquals("당류가 1회 제공량 기준 하루 기준치의 42%예요.",
                TopReasonBuilder.build(Nutrient.SUGAR, new BigDecimal("42"), new BigDecimal("21"), 5.0));
        // 열량 500kcal / 2000 = 25%
        assertEquals("열량이 1회 제공량 기준 하루 기준치의 25%예요.",
                TopReasonBuilder.build(Nutrient.CALORIES, new BigDecimal("500"), new BigDecimal("450"), 40.0));
    }

    @Test
    void 제공량_값이_없으면_100g_기준값과_감점_기준으로() {
        assertEquals("나트륨이 100g당 1000mg으로 기준(120mg)을 넘어요.",
                TopReasonBuilder.build(Nutrient.SODIUM, null, new BigDecimal("1000.0"), 120.0));
        // 트랜스지방은 1일 기준치가 없어 제공량 값이 있어도 100g 기준 문장
        assertEquals("트랜스지방이 100g당 0.5g으로 기준(0.2g)을 넘어요.",
                TopReasonBuilder.build(Nutrient.TRANS_FAT, new BigDecimal("1"), new BigDecimal("0.5"), 0.2));
        assertEquals("포화지방이 100g당 8g이에요.",
                TopReasonBuilder.build(Nutrient.SATURATED_FAT, BigDecimal.ZERO, new BigDecimal("8.00"), null));
    }

    @Test
    void 감점_요인이_없으면_null() {
        assertNull(TopReasonBuilder.build(null, BigDecimal.ONE, BigDecimal.ONE, 1.0));
    }

    @Test
    void 조사_이가_받침으로_결정된다() {
        assertFalse(TopReasonBuilder.hasFinalConsonant("당류"));
        assertTrue(TopReasonBuilder.hasFinalConsonant("나트륨"));
        assertTrue(TopReasonBuilder.hasFinalConsonant("열량"));
        assertEquals("21", TopReasonBuilder.fmt(new BigDecimal("21.0")));
        assertEquals("1.5", TopReasonBuilder.fmt(new BigDecimal("1.50")));
    }
}
