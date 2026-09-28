package com.example.nutriuniv.domain.grade.entity;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 성분별 기준값 (기능명세서 4.1 「계산에 쓰는 기준값을 코드에 박지 말고 DB에서 읽는다」, 5.2 「실제 값과 기준값」).
 * 버전(recalibration_id)·목표·성분당 1행. 재산출은 이 값을 바꾸지 않고 새 버전으로 복사한다 — 바뀌는 것은 앵커와 컷오프.
 * <ul>
 *   <li>BONUS: threshold = 만점선(이 값 이상이면 weight 만점)</li>
 *   <li>PENALTY: threshold = 감점 시작값, max_value = 최대 감점(weight)에 도달하는 값</li>
 *   <li>CALORIE: threshold = 에너지밀도 0점(kcal/100g), max_value = 상한 M. null 이면 열량구간별 상한(EerBand.energyDensityCap).
 *       weight 부호: 감량 −1 · 근육 증가 +1 · 일반 0</li>
 * </ul>
 */
@Entity
@Table(name = "grade_criteria",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_criteria_version_goal_nutrient",
                columnNames = {"recalibration_id", "goal", "nutrient"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recalibration_id", nullable = false)
    private Long recalibrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalType goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nutrient nutrient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CriterionDirection direction;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;              // 성분별 비중 (v1 은 전부 1, CALORIE 만 목표별 부호)

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal threshold;           // 만점선(BONUS) · 감점 시작값(PENALTY) · 에너지밀도 0점(CALORIE)

    @Column(name = "max_value", precision = 10, scale = 3)
    private BigDecimal maxValue;            // 최대 감점 도달값(PENALTY) · 에너지밀도 상한(CALORIE, null=열량구간별)

    @Column(length = 10)
    private String unit;

    @Column(name = "reason_template", length = 255)
    private String reasonTemplate;          // 5.2 「그 목표에서 왜 중요한지 한 줄」

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public static GradeCriterion create(Long recalibrationId, GoalType goal, Nutrient nutrient,
                                        CriterionDirection direction, BigDecimal weight,
                                        BigDecimal threshold, BigDecimal maxValue,
                                        String unit, String reasonTemplate) {
        GradeCriterion c = new GradeCriterion();
        c.recalibrationId = recalibrationId;
        c.goal            = goal;
        c.nutrient        = nutrient;
        c.direction       = direction;
        c.weight          = weight;
        c.threshold       = threshold;
        c.maxValue        = maxValue;
        c.unit            = unit;
        c.reasonTemplate  = reasonTemplate;
        c.isActive        = true;
        return c;
    }

    /** 재산출 시 새 버전으로 복사 (값 불변). */
    public GradeCriterion copyTo(Long newRecalibrationId) {
        return create(newRecalibrationId, goal, nutrient, direction, weight, threshold, maxValue, unit, reasonTemplate);
    }
}
