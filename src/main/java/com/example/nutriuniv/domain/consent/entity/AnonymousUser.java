package com.example.nutriuniv.domain.consent.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 익명 사용자 — 개인정보 동의(POST /onboarding/consent) 시점에 서버가 발급한다 (기능명세서 1.2).
 * 클라이언트는 이 ID 를 localStorage 에 두고 X-Anonymous-Id 헤더로 보낸다.
 *
 * <p>로그인 시(7.2) 저장·목표·동의 기록이 계정으로 병합되면 merged_user_id 가 채워지고,
 * 그 뒤로 이 ID 는 소유자로 인정하지 않는다(다시 쓰면 이후 기록이 영영 병합 불가). 로그아웃은 새 ID 를 발급한다.
 * personal/health 동의 플래그는 조회용 캐시 — 원장은 consents 테이블(append-only).
 */
@Entity
@Table(name = "anonymous_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_anonymous_users_anonymous_id", columnNames = "anonymous_id"),
        indexes = @Index(name = "idx_anonymous_users_merged_user", columnList = "merged_user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonymousUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_id", nullable = false, length = 36)
    private String anonymousId;

    @Column(name = "personal_info_agreed", nullable = false)
    private boolean personalInfoAgreed = false;

    @Column(name = "health_info_agreed", nullable = false)
    private boolean healthInfoAgreed = false;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    // 병합된 계정 (null = 아직 익명)
    @Column(name = "merged_user_id")
    private Long mergedUserId;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // ── 생성 ─────────────────────────────────────────────────────────────────────

    /** 개인정보 동의 완료 상태로 새 익명 ID 발급 (동의 없이는 ID 가 생기지 않는다). */
    public static AnonymousUser issue() {
        AnonymousUser a = new AnonymousUser();
        a.anonymousId = UUID.randomUUID().toString();
        a.personalInfoAgreed = true;
        a.issuedAt = LocalDateTime.now();
        a.lastSeenAt = a.issuedAt;
        return a;
    }

    // ── 상태 변경 ──────────────────────────────────────────────────────────────────

    public boolean isMerged() {
        return mergedUserId != null;
    }

    public void agreePersonalInfo() {
        this.personalInfoAgreed = true;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void agreeHealthInfo() {
        this.healthInfoAgreed = true;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void revokeHealthInfo() {
        this.healthInfoAgreed = false;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void completeOnboarding() {
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = LocalDateTime.now();
        }
    }

    public void markMerged(Long userId) {
        this.mergedUserId = userId;
        this.mergedAt = LocalDateTime.now();
    }
}
