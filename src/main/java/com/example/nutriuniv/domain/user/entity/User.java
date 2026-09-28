package com.example.nutriuniv.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oauth_provider", nullable = false, length = 20)
    private String oauthProvider;

    @Column(name = "oauth_id", nullable = false, length = 255)
    private String oauthId;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 10)
    private String role = "USER";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ── 동의 관련 필드 ────────────────────────────────────────────────────────────

    @Column(name = "personal_info_agreed", nullable = false)
    private boolean personalInfoAgreed = false;  // ① 개인정보 수집·이용 동의 (필수)

    @Column(name = "health_info_agreed", nullable = false)
    private boolean healthInfoAgreed = false;    // ② 건강정보 수집·이용 동의 (선택)

    @Column(name = "age_confirmed", nullable = false)
    private boolean ageConfirmed = false;        // ③ 만 14세 이상 확인 (필수)

    @Column(name = "consented_at")
    private LocalDateTime consentedAt;           // 동의 시각

    // 온보딩 완료 시각 (bootstrap 라우팅 분기: 안 함→찍기 화면, 완료→홈). POST /onboarding/complete 에서 기록.
    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    // ── Audit ─────────────────────────────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── 생성 ─────────────────────────────────────────────────────────────────────

    public static User create(String oauthProvider, String oauthId,
                              String name, String gender, LocalDate birthDate, String email,
                              boolean personalInfoAgreed, boolean healthInfoAgreed, boolean ageConfirmed) {
        User u = new User();
        u.oauthProvider      = oauthProvider;
        u.oauthId            = oauthId;
        u.name               = name;
        u.nickname           = name;  // 가입 시 name으로 자동 설정
        u.gender             = gender;
        u.birthDate          = birthDate;
        u.email              = email;
        u.personalInfoAgreed = personalInfoAgreed;
        u.healthInfoAgreed   = healthInfoAgreed;
        u.ageConfirmed       = ageConfirmed;
        u.consentedAt        = LocalDateTime.now();
        return u;
    }

    // ── 수정 ─────────────────────────────────────────────────────────────────────

    public void update(String name, String email, String nickname, String gender, LocalDate birthDate) {
        if (name != null)      this.name      = name;
        if (email != null)     this.email     = email;
        if (nickname != null)  this.nickname  = nickname;
        if (gender != null)    this.gender    = gender;
        if (birthDate != null) this.birthDate = birthDate;
    }

    public void deactivate() {
        this.isActive  = false;
        this.deletedAt = LocalDateTime.now();
        this.oauthId   = "DELETED_" + this.id;  // ✅ 추가: unique 충돌 방지 (재가입 허용)
    }

    // ── 동의 플래그 (조회용 캐시 — 원장은 consents 테이블) ──────────────────────────

    public void agreePersonalInfo() {
        this.personalInfoAgreed = true;
        this.consentedAt = LocalDateTime.now();
    }

    public void agreeHealthInfo() {
        this.healthInfoAgreed = true;
        this.consentedAt = LocalDateTime.now();
    }

    public void revokeHealthInfo() {
        this.healthInfoAgreed = false;
    }

    public void completeOnboarding() {
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = LocalDateTime.now();
        }
    }

    public void updateRole(String role) {
        this.role = role;
    }
}