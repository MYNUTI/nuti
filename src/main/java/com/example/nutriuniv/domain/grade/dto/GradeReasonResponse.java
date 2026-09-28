package com.example.nutriuniv.domain.grade.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /products/{productId}/grade-reason 출력 (기능명세서 5.2 「왜 C인가요?」).
 * ① 감점 요인의 실제 값과 기준값 ② 그 목표에서 왜 중요한지 한 줄 ③ 분류 내 위치는 3차 전까지 주지 않는다.
 */
@Getter
@Builder
public class GradeReasonResponse {

    private String appliedGoal;                 // 근거를 계산한 목표

    private List<Factor> factors;               // 감점 요인, 감점이 큰 순. 감점이 없으면 빈 배열

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String fiberNotice;                 // 식이섬유 값이 없어 0 으로 계산했을 때만

    private Methodology methodology;

    @Getter
    @Builder
    public static class Factor {
        private String nutrient;                // SUGAR 등 코드
        private String label;                   // 당류
        private BigDecimal value;               // 100g 기준 실제 값
        private String unit;
        private BigDecimal threshold;           // 감점 시작 기준값 (grade_criteria.threshold)
        private String sentence;                // 그 목표에서 왜 중요한지 (grade_criteria.reason_template)
    }

    @Getter
    @Builder
    public static class Methodology {
        private List<String> steps;             // 산정 단계
        private String note;                    // 목표별 배점 차이
    }
}
