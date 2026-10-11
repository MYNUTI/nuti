package com.example.nutriuniv.domain.grade.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 등급 기준 버전 (기능명세서 4.2·10.3). 앵커(grade_anchors)·기준값(grade_criteria)·컷오프(grade_cutoffs)가 이 id 에 매달린다.
 * <p>재산출 = 새 버전 행 + 기준 세트 + product_grades 전량 재계산을 한 트랜잭션으로. 실패하면 되돌리고 ROLLED_BACK 기록만 남긴다.
 * 재산출 전후로 같은 제품의 등급이 바뀌므로 applied_at 이 「적용 시점」이다.
 */
@Entity
@Table(name = "grade_recalibrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_recalibrations_version", columnNames = "version"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeRecalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String version;                 // v1, v2, …

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RecalibrationStatus status;

    @Column(name = "base_date")
    private LocalDate baseDate;             // 재산출 기준일 (4.2 「기준일·모수와 함께 버전 저장」)

    @Column(name = "target_count")
    private Integer targetCount;            // 모수 — 채점 대상(분석 완료) 제품 수

    // 등급 분포 비교(10.3) — {"WEIGHT_LOSS":{"A":123,…},…} JSON 문자열. 감사·화면용이라 텍스트로 둔다.
    @Column(name = "before_distribution", columnDefinition = "text")
    private String beforeDistribution;

    @Column(name = "after_distribution", columnDefinition = "text")
    private String afterDistribution;

    @Column(length = 500)
    private String memo;

    @Column(name = "created_by")
    private Long createdBy;                 // 실행한 관리자 (초기 시드는 null)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;        // APPLIED 만 채워진다

    public static GradeRecalibration applied(String version, LocalDate baseDate, Integer targetCount,
                                             String beforeDistribution, String afterDistribution,
                                             String memo, Long createdBy) {
        GradeRecalibration r = new GradeRecalibration();
        r.version            = version;
        r.status             = RecalibrationStatus.APPLIED;
        r.baseDate           = baseDate;
        r.targetCount        = targetCount;
        r.beforeDistribution = beforeDistribution;
        r.afterDistribution  = afterDistribution;
        r.memo               = memo;
        r.createdBy          = createdBy;
        r.createdAt          = LocalDateTime.now();
        r.appliedAt          = r.createdAt;
        return r;
    }

    public static GradeRecalibration rolledBack(String version, LocalDate baseDate, String memo, Long createdBy) {
        GradeRecalibration r = new GradeRecalibration();
        r.version   = version;
        r.status    = RecalibrationStatus.ROLLED_BACK;
        r.baseDate  = baseDate;
        r.memo      = memo;
        r.createdBy = createdBy;
        r.createdAt = LocalDateTime.now();
        return r;
    }
}
