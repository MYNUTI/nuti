package com.example.nutriuniv.domain.goal.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 소유자(user_id XOR anonymous_id)당 목표 1행. 키·몸무게·나이·활동량은 받지 않는다(2차 명세).
 * 로그인 시 병합(7.2): 계정에 목표가 없을 때만 익명 목표를 이관한다.
 * XOR CHECK 제약은 db/manual SQL. nullable 컬럼의 UNIQUE 는 PostgreSQL 에서 NULL 중복을 허용하므로 그대로 쓴다.
 */
@Entity
@Table(name = "user_goals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_goals_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_user_goals_anon", columnNames = "anonymous_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalType goal;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static UserGoal create(Owner owner, GoalType goal) {
        UserGoal g = new UserGoal();
        g.userId = owner.userId();
        g.anonymousId = owner.anonymousId();
        g.goal = goal;
        g.appliedAt = LocalDateTime.now();
        g.updatedAt = g.appliedAt;
        return g;
    }

    public void change(GoalType goal) {
        if (this.goal != goal) {
            this.goal = goal;
            this.appliedAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    /** 병합 시 익명 행을 계정으로 옮긴다 (계정에 목표가 없을 때만 호출). */
    public void transferTo(Long userId) {
        this.userId = userId;
        this.anonymousId = null;
        this.updatedAt = LocalDateTime.now();
    }
}
