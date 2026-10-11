package com.example.nutriuniv.domain.ranking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스 분류별 배치 결과 메타 — 분모(전체 제품 수)·분석 완료 수·A/D 수·게이트 통과 여부·계산 시각.
 * 「단백질 음료 128개 중 121개 분석 완료 (95%)」 와 「분류를 여는 조건(300건 + A·D)」 판정이 여기서 나온다.
 * 게이트는 일반(GENERAL) 기준 등급으로 판정한다 — 분류 목록(/rankings/categories)에 목표 파라미터가 없고, 데이터 현황 대시보드(10.5)와 같은 기준.
 */
@Entity
@Table(name = "ranking_category_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingCategoryStat {

    @Id
    @Column(name = "ranking_category_id")
    private Long rankingCategoryId;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "analyzed_count", nullable = false)
    private long analyzedCount;

    @Column(name = "a_count", nullable = false)
    private long aCount;

    @Column(name = "d_count", nullable = false)
    private long dCount;

    @Column(name = "gate_passed", nullable = false)
    private boolean gatePassed;

    /** 저장된 순위 행 수(목표 3종 합). 게이트 미통과면 0. */
    @Column(name = "ranked_rows", nullable = false)
    private int rankedRows;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    public double analyzedRatio() {
        return totalCount == 0 ? 0.0 : Math.round((double) analyzedCount / totalCount * 10000.0) / 10000.0;
    }
}
