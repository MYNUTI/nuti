package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EerBandTest {

    @Test
    void 감량_에너지밀도_상한_M_구_공식_그대로() {
        assertEquals(400.0,    EerBand.B1500.energyDensityCap(), 1e-9);
        assertEquals(627.2727, EerBand.B2000.energyDensityCap(), 1e-3);
        assertEquals(854.5454, EerBand.B2500.energyDensityCap(), 1e-3);
        assertEquals(900.0,    EerBand.B3000.energyDensityCap(), 1e-9);   // 1081.8 → 900 클램프
        assertEquals(EerBand.B2000.energyDensityCap(), EerBand.energyDensityCap(EerBand.GENERAL_SLOT), 1e-9);
    }

    @Test
    void 슬롯_일반은_0_그외는_구간값() {
        assertEquals(0,    EerBand.defaultSlot(GoalType.GENERAL));
        assertEquals(2000, EerBand.defaultSlot(GoalType.WEIGHT_LOSS));
        assertEquals(2000, EerBand.defaultSlot(GoalType.MUSCLE_GAIN));
        assertEquals(List.of(0), EerBand.slots(GoalType.GENERAL));
        assertEquals(List.of(1500, 2000, 2500, 3000), EerBand.slots(GoalType.MUSCLE_GAIN));
    }
}
