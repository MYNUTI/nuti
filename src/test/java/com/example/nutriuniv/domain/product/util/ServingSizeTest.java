package com.example.nutriuniv.domain.product.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ServingSizeTest {

    @Test
    void 숫자와_단위를_읽는다() {
        ServingSize s = ServingSize.parse("300g");
        assertEquals(new BigDecimal("300"), s.amount());
        assertEquals("g", s.unit());
        assertEquals("100g", ServingSize.basisOf(s));

        assertEquals(new BigDecimal("30"), ServingSize.parse("1봉지(30g)").amount());
        assertEquals(new BigDecimal("12.5"), ServingSize.parse("12.5 g").amount());
    }

    @Test
    void kg_L_은_g_mL_로_mL_제품은_100mL_기준() {
        assertEquals(new BigDecimal("1500"), ServingSize.parse("1.5kg").amount());
        ServingSize l = ServingSize.parse("1L");
        assertEquals(new BigDecimal("1000"), l.amount());
        assertEquals("mL", l.unit());
        ServingSize ml = ServingSize.parse("1,000 mL");
        assertEquals(new BigDecimal("1000"), ml.amount());
        assertEquals("100mL", ServingSize.basisOf(ml));
        assertEquals("300", ServingSize.parse("300g").amount().toPlainString());
    }

    @Test
    void 숫자가_없으면_글자만_비어있으면_null() {
        ServingSize s = ServingSize.parse("1개");
        assertEquals("1개", s.text());
        assertNull(s.amount());
        assertNull(ServingSize.parse(" "));
        assertEquals("100g", ServingSize.basisOf(null));
    }
}
