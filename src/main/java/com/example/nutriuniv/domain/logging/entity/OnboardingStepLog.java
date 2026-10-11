package com.example.nutriuniv.domain.logging.entity;

import com.example.nutriuniv.domain.logging.dto.LogContext;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 온보딩 단계 이벤트 (API 명세 /logging/onboarding-step) — fire-and-forget.
 * step: CONSENT·CAMERA·START·PICKER·SCAN·FIRST_RESULT·GOAL·DONE / action: VIEW·ACCEPT·SKIP / reason: PICKER 진입 이유 5종.
 * 동의 전에는 익명 ID 가 없어 세션 ID 로 한 세션 안의 흐름만 본다. IP 는 남기지 않는다.
 */
@Entity
@Table(name = "onboarding_step_logs",
        indexes = {
                @Index(name = "idx_onboarding_step_logs_session", columnList = "session_id"),
                @Index(name = "idx_onboarding_step_logs_step_created", columnList = "step, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingStepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 15)
    private String step;

    @Column(nullable = false, length = 10)
    private String action;

    @Column(length = 20)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static OnboardingStepLog create(LogContext ctx, String step, String action, String reason) {
        OnboardingStepLog l = new OnboardingStepLog();
        l.anonymousId = ctx.anonymousId();
        l.sessionId   = ctx.sessionId();
        l.userId      = ctx.userId();
        l.step        = step;
        l.action      = action;
        l.reason      = reason;
        l.createdAt   = LocalDateTime.now();
        return l;
    }
}
