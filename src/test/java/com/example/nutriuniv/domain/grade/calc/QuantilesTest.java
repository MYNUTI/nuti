package com.example.nutriuniv.domain.grade.calc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantilesTest {

    private static final double[] ONE_TO_FIVE = {1, 2, 3, 4, 5};

    @Test
    void 선형_보간() {
        assertEquals(3.0,  Quantiles.linear(ONE_TO_FIVE, 0.50), 1e-12);
        assertEquals(4.2,  Quantiles.linear(ONE_TO_FIVE, 0.80), 1e-12);   // pos 3.2 → 4 + 0.2
        assertEquals(1.04, Quantiles.linear(ONE_TO_FIVE, 0.01), 1e-12);
        assertEquals(4.96, Quantiles.linear(ONE_TO_FIVE, 0.99), 1e-12);
        assertEquals(1.0,  Quantiles.linear(ONE_TO_FIVE, 0.0), 1e-12);
        assertEquals(5.0,  Quantiles.linear(ONE_TO_FIVE, 1.0), 1e-12);
    }

    @Test
    void 원소_하나면_그_값() {
        assertEquals(7.0, Quantiles.linear(new double[]{7}, 0.99), 1e-12);
    }

    @Test
    void 빈_배열과_범위_밖_q는_예외() {
        assertThrows(IllegalArgumentException.class, () -> Quantiles.linear(new double[0], 0.5));
        assertThrows(IllegalArgumentException.class, () -> Quantiles.linear(ONE_TO_FIVE, 1.5));
    }
}
