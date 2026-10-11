package com.example.nutriuniv.domain.grade.entity;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 등급 설명·안내 문구 (기능명세서 4.1 「화면에 보여줄 등급 설명 문구도 서버가 준다」, 5.1 출처 문구·배지).
 * 재산출로 산식이 바뀌어도 설명이 어긋나지 않도록 DB 에 둔다. /grades/guide·등급 근거·결과 화면(핵심 루프 브랜치)이 읽는다.
 * <p>copy_code 예: GUIDE_STEPS(산정 단계) · GUIDE_GOAL(목표별 배점 차이, goal 별) · FIBER_NOTICE(식이섬유 안내) ·
 * GRADE_DESC_A~E(등급 설명) · BADGE_PRE_CATEGORY(「분류 비교 전」) · SOURCE_NOTE(출처 문구)
 */
@Entity
@Table(name = "grade_copies",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_copies_code_goal", columnNames = {"copy_code", "goal"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "copy_code", nullable = false, length = 40)
    private String copyCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GoalType goal;                  // null = 목표 공통

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(length = 20)
    private String version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static GradeCopy create(String copyCode, GoalType goal, String title, String body, int displayOrder, String version) {
        GradeCopy c = new GradeCopy();
        c.copyCode     = copyCode;
        c.goal         = goal;
        c.title        = title;
        c.body         = body;
        c.displayOrder = displayOrder;
        c.version      = version;
        c.updatedAt    = LocalDateTime.now();
        return c;
    }
}
