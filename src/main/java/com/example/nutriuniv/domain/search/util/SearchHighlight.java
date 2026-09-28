package com.example.nutriuniv.domain.search.util;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Nutrient;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 검색 결과 한 줄의 목표 기준 위반 문구 (기능명세서 6.1 「당류 21g — 감량 기준 초과」). 서버가 완성 문장으로 만든다.
 * 입력은 그 제품의 가장 큰 감점 요인(product_grades.top_penalty_nutrient)과 100g 기준값. 감점이 없거나 영양정보 부족이면 null.
 */
public final class SearchHighlight {

    private SearchHighlight() {}

    public static String build(GoalType goal, Nutrient nutrient, BigDecimal per100g) {
        if (nutrient == null || per100g == null) return null;
        String value = per100g.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + nutrient.unit();
        String basis = goal == null || goal == GoalType.GENERAL ? "기준 초과" : goal.label() + " 기준 초과";
        return nutrient.label() + " " + value + " — " + basis;
    }
}
