package com.example.nutriuniv.domain.grade.entity;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 등급 컷오프 + 등급 라벨 (기능명세서 4.1 ⑤·4.2). 버전·목표·등급당 1행. score ≥ min_score 인 가장 높은 등급이 그 제품의 등급.
 * E 는 min_score 0. 재산출은 점수 분포의 80/60/40/20% 지점을 A/B/C/D 의 min_score 로 다시 잡는다(A = 상위 20%).
 * 라벨 문구 원칙(5.1): 「나쁩니다」 대신 「잘 안 맞아요」.
 */
@Entity
@Table(name = "grade_cutoffs",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_cutoffs_version_goal_grade",
                columnNames = {"recalibration_id", "goal", "grade"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeCutoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recalibration_id", nullable = false)
    private Long recalibrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalType goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Grade grade;

    @Column(name = "min_score", nullable = false, precision = 5, scale = 1)
    private BigDecimal minScore;

    @Column(length = 50)
    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public static GradeCutoff create(Long recalibrationId, GoalType goal, Grade grade, BigDecimal minScore, String label) {
        GradeCutoff c = new GradeCutoff();
        c.recalibrationId = recalibrationId;
        c.goal     = goal;
        c.grade    = grade;
        c.minScore = minScore;
        c.label    = label;
        c.isActive = true;
        return c;
    }
}
