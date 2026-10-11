package com.example.nutriuniv.domain.product.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 기능명세서 4.1 — 분석 완료 판정은 7종, 식이섬유는 판정에 넣지 않는다. */
class ProductStatusTest {

    private static final BigDecimal V = new BigDecimal("1.0");

    @Test
    void 일곱_종_전부_있으면_완료() {
        assertTrue(ProductStatus.isComplete(V, V, V, V, V, V, V));
        assertTrue(ProductStatus.isComplete(BigDecimal.ZERO, V, V, V, V, V, V));   // 0 은 값이다 — 결측이 아니다
    }

    @Test
    void 하나라도_없으면_부족() {
        assertFalse(ProductStatus.isComplete(V, V, V, null, V, V, V));
        assertFalse(ProductStatus.isComplete(null, null, null, null, null, null, null));
    }

    @Test
    void 영양정보_행이_없으면_부족() {
        assertEquals(ProductStatus.INSUFFICIENT, ProductStatus.of(null));
    }
}
