package com.example.nutriuniv.domain.grade.dto;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Grade;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * POST /admin/grade-recalibrations/preview 출력 (기능명세서 10.3 「실행 전에 미리보기 — 전후 분포 비교 후 승인」).
 * 저장하지 않는 순수 계산이며, 같은 데이터로 실행하면 같은 값이 적용된다.
 */
@Getter
@Builder
public class RecalibrationPreviewResponse {

    private String basedOnVersion;                        // 기준값(criteria)을 승계하는 현행 버전
    private int targetCount;                              // 모수 — 분석 완료 제품 수
    private Map<GoalType, AnchorDto> anchors;             // 목표별 새 P1/P99
    private Map<GoalType, Map<Grade, BigDecimal>> cutoffs;         // 목표별 A~D min_score (E 는 0)
    private Map<GoalType, Map<Grade, Long>> beforeDistribution;    // 현재 product_grades 분포 (조회 슬롯 기준)
    private Map<GoalType, Map<Grade, Long>> afterDistribution;     // 새 기준 적용 시 분포

    @Getter
    @Builder
    public static class AnchorDto {
        private BigDecimal p1;
        private BigDecimal p99;
    }
}
