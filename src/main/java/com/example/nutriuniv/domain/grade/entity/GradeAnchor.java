package com.example.nutriuniv.domain.grade.entity;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 정규화 앵커 (기능명세서 4.1 ④·4.2). 목표별로 raw 점수 분포의 P1 → 0점, P99 → 100점.
 * 목표 단위 값이라 성분 단위 표(grade_criteria)가 아니라 따로 둔다 — 재산출(4.2)이 다시 계산하는 값은 이것과 컷오프 둘이다.
 */
@Entity
@Table(name = "grade_anchors",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_anchors_version_goal", columnNames = {"recalibration_id", "goal"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recalibration_id", nullable = false)
    private Long recalibrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalType goal;

    @Column(name = "anchor_p1", nullable = false, precision = 10, scale = 3)
    private BigDecimal p1;

    @Column(name = "anchor_p99", nullable = false, precision = 10, scale = 3)
    private BigDecimal p99;

    public static GradeAnchor create(Long recalibrationId, GoalType goal, BigDecimal p1, BigDecimal p99) {
        GradeAnchor a = new GradeAnchor();
        a.recalibrationId = recalibrationId;
        a.goal = goal;
        a.p1 = p1;
        a.p99 = p99;
        return a;
    }
}
