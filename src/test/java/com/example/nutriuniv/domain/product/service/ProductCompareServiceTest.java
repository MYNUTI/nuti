package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.product.dto.AlternativesResponse.Diff;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCompareServiceTest {

    private static Map<Nutrient, BigDecimal> m(String cal, String prot, String sug, String na) {
        Map<Nutrient, BigDecimal> out = new EnumMap<>(Nutrient.class);
        out.put(Nutrient.CALORIES, new BigDecimal(cal));
        out.put(Nutrient.PROTEIN, new BigDecimal(prot));
        out.put(Nutrient.SUGAR, new BigDecimal(sug));
        out.put(Nutrient.SODIUM, new BigDecimal(na));
        return out;
    }

    @Test
    void 차이는_목표_기준_나아진_성분_중_기준치_퍼센트가_큰_두개() {
        Map<Nutrient, BigDecimal> base = m("450", "5", "30", "300");
        Map<Nutrient, BigDecimal> cand = m("421", "16", "27.2", "350");   // 칼로리 −29(1.45%) 단백질 +11(20%) 당류 −2.8(2.8%) 나트륨 +50(나빠짐)

        List<String> wl = ProductCompareService.diffs(GoalType.WEIGHT_LOSS, base, cand).stream().map(Diff::text).toList();
        assertEquals(List.of("단백질 +11g", "당류 −2.8g"), wl);

        // 근육 증가: 칼로리가 줄어든 건 나아진 게 아니다
        List<String> mg = ProductCompareService.diffs(GoalType.MUSCLE_GAIN, base, m("421", "5", "30", "300")).stream().map(Diff::text).toList();
        assertEquals(List.of(), mg);
    }
}
