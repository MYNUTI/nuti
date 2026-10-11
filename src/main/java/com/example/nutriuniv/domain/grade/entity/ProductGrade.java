package com.example.nutriuniv.domain.grade.entity;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 제품당 9슬롯 사전계산 등급 (기능명세서 공통 규칙·4.1) — 구 product_pns_by_eer 개조.
 * <p>슬롯 = (goal, eer_band): 감량×4구간 + 근육 증가×4구간 + 일반×1. 일반은 열량구간과 무관하므로 eer_band=0 한 슬롯.
 * 1·2차 조회는 구간 2000(일반은 0)만 쓴다 — {@code EerBand.defaultSlot(goal)}.
 * <p>쓰기는 배치(GradeBatchService)가 JDBC 로 전량 교체한다. 이 엔티티는 DDL 생성·조회용.
 * products FK 는 두지 않는다(구 테이블과 동일, 배치 적재 속도) — 제품은 비활성화만 하고 삭제하지 않는다. 전체 초기화는 ProductService.resetAll 이 함께 비운다.
 */
@Entity
@Table(name = "product_grades",
        indexes = @Index(name = "idx_product_grades_goal_band_grade", columnList = "goal, eer_band, grade"))
@IdClass(ProductGrade.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductGrade {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "goal", length = 20)
    private GoalType goal;

    @Id
    @Column(name = "eer_band")
    private Integer eerBand;

    @Column(precision = 6, scale = 1, nullable = false)
    private BigDecimal score;              // 0.0 ~ 100.0 (산정 ④ 정규화)

    @Enumerated(EnumType.STRING)
    @Column(length = 1, nullable = false)
    private Grade grade;                   // 산정 ⑤ 컷오프 적용

    @Column(precision = 5, scale = 2)
    private BigDecimal percentile;         // 대분류 안 백분위(클수록 좋음) — 동점·분포용, 1·2차 화면 미노출

    @Enumerated(EnumType.STRING)
    @Column(name = "top_penalty_nutrient", length = 20)
    private Nutrient topPenaltyNutrient;   // 가장 큰 감점 요인 — topReason 문장 조립 키(2.3·5.2). 감점이 없으면 null

    @Column(name = "recalibration_id")
    private Long recalibrationId;          // 이 값을 만든 기준 버전 (grade_recalibrations.id)

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── 복합 PK ───────────────────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long productId;
        private GoalType goal;
        private Integer eerBand;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk that)) return false;
            return Objects.equals(productId, that.productId)
                    && goal == that.goal
                    && Objects.equals(eerBand, that.eerBand);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, goal, eerBand);
        }
    }
}
