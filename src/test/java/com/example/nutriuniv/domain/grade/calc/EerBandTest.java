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
        assertEquals(EerBand.B2000.energyDensityCap(), EerBand.ofOrDefault(null).energyDensityCap(), 1e-9);
    }

    @Test
    void 슬롯은_열량구간값() {
        assertEquals(2000, EerBand.defaultSlot(GoalType.WEIGHT_LOSS));
        assertEquals(2000, EerBand.defaultSlot(GoalType.MUSCLE_GAIN));
        assertEquals(List.of(1500, 2000, 2500, 3000), EerBand.slots(GoalType.WEIGHT_LOSS));
        assertEquals(List.of(1500, 2000, 2500, 3000), EerBand.slots(GoalType.MUSCLE_GAIN));
    }

    @Test
    void 몸_정보_칼로리는_가까운_구간으로_같으면_위로() {
        assertEquals(EerBand.B1500, EerBand.nearest(1100));
        assertEquals(EerBand.B2000, EerBand.nearest(1750));     // 1500 · 2000 가운데 → 위
        assertEquals(EerBand.B2000, EerBand.nearest(2240));
        assertEquals(EerBand.B2500, EerBand.nearest(2260));
        assertEquals(EerBand.B3000, EerBand.nearest(4200));
    }
}
