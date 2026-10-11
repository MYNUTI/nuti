package com.example.nutriuniv.domain.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 결과 화면·스캔 응답의 grade 블록 (API 명세: score·grade·label·badgeText·fiberIncluded).
 * score 0~100 은 색+문자+막대 렌더용(5.1). badgeText 는 1·2차 「분류 비교 전」 고정(grade_copies). 영양정보 부족이면 블록 자체가 없다.
 */
@Getter
@Builder
public class GradeBadge {

    private BigDecimal score;
    private String grade;
    private String label;
    private String badgeText;
    private boolean fiberIncluded;      // 식이섬유 값이 있었는지 (없으면 0 으로 계산됨 — 4.1)
}
