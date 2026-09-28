package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.grade.entity.Nutrient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 감점 요인 1개를 완성 문장으로 (기능명세서 2.3 「감점 요인은 하나만 주고, 문장까지 서버가 만든다」).
 * <ul>
 *   <li>1회 제공량 값과 1일 영양성분 기준치가 있으면: 「당류가 1회 제공량 기준 하루 기준치의 42%예요.」</li>
 *   <li>아니면 100g 기준값과 감점 시작 기준값으로: 「트랜스지방이 100g당 0.5g으로 기준(0.2g)을 넘어요.」</li>
 * </ul>
 * 순수 함수. 문구 원칙(5.1): 「나쁩니다」 같은 판정 대신 사실을 말한다.
 */
public final class TopReasonBuilder {

    /** 1일 영양성분 기준치 (식약처 고시). 트랜스지방·단백질·식이섬유는 감점 문장에 쓰지 않아 제외. */
    private static final Map<Nutrient, Double> DAILY_VALUE = Map.of(
            Nutrient.CALORIES,      2000.0,
            Nutrient.SODIUM,        2000.0,
            Nutrient.SUGAR,          100.0,
            Nutrient.SATURATED_FAT,   15.0,
            Nutrient.CHOLESTEROL,    300.0
    );

    private TopReasonBuilder() {}

    /**
     * @param nutrient     가장 큰 감점 요인 (product_grades.top_penalty_nutrient). null 이면 문장 없음
     * @param servingValue 1회 제공량 기준 값 (없으면 null)
     * @param per100gValue 100g 기준 값 (없으면 null)
     * @param threshold    그 목표의 감점 시작 기준값 (grade_criteria.threshold, 없으면 null)
     */
    public static String build(Nutrient nutrient, BigDecimal servingValue, BigDecimal per100gValue, Double threshold) {
        if (nutrient == null) return null;

        Double dv = DAILY_VALUE.get(nutrient);
        if (dv != null && servingValue != null && servingValue.signum() > 0) {
            long pct = Math.round(servingValue.doubleValue() / dv * 100.0);
            return subject(nutrient) + " 1회 제공량 기준 하루 기준치의 " + pct + "%예요.";
        }
        if (per100gValue != null) {
            String base = subject(nutrient) + " 100g당 " + fmt(per100gValue) + nutrient.unit();
            if (threshold != null) {
                return base + "으로 기준(" + fmt(threshold) + nutrient.unit() + ")을 넘어요.";
            }
            return base + "이에요.";
        }
        return subject(nutrient) + " 가장 큰 감점 요인이에요.";
    }

    /** 성분명 + 주격 조사(이/가). */
    static String subject(Nutrient n) {
        return n.label() + (hasFinalConsonant(n.label()) ? "이" : "가");
    }

    /** 마지막 글자에 받침이 있는가 (한글 음절만 판단, 그 외 문자는 받침 없음으로). */
    static boolean hasFinalConsonant(String s) {
        if (s == null || s.isEmpty()) return false;
        char c = s.charAt(s.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return false;
        return (c - 0xAC00) % 28 != 0;
    }

    /** 소수 1자리 반올림 후 불필요한 0 제거: 21.0 → 21, 1.50 → 1.5. */
    static String fmt(Number v) {
        BigDecimal b = v instanceof BigDecimal bd ? bd : BigDecimal.valueOf(v.doubleValue());
        return b.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
