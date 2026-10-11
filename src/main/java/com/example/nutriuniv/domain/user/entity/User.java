package com.example.nutriuniv.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원. 2차부터 소셜 로그인 즉시 가입(등록 단계 폐지)이라 <b>이메일 외 프로필은 수집하지 않는다</b> — name·gender·birthDate 는 nullable
 * (기존 컬럼의 NOT NULL 해제는 db/manual/05_auth_merge.sql, Hibernate 가 못 함). 1차 회원의 기존 값은 그대로 둔다.
 * 닉네임은 서버가 「사용자{id}」로 정하고 사용자가 바꿀 수 있다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    public static final String DEFAULT_NICKNAME_PREFIX = "사용자";

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

    // ── 프로필 (2차부터 미수집 — 1차 회원 값만 남아 있다) ────────────────────────────

    @Column(length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 10)
    private String gender;

    @Column(name = "birth_date")
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
    private boolean ageConfirmed = false;        // ③ 만 14세 이상 확인 (필수) — 2차는 동의 항목 AGE_OVER_14 로 받는다

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

    /**
     * 소셜 로그인 즉시 가입 (API 명세 /auth/oauth 「신규회원도 즉시 가입+토큰」). 이메일 외 프로필은 받지 않는다.
     * 저장 뒤 {@link #assignDefaultNickname()} 로 닉네임을 확정한다(id 가 필요).
     */
    public static User register(String oauthProvider, String oauthId, String email) {
        User u = new User();
        u.oauthProvider = oauthProvider;
        u.oauthId       = oauthId;
        u.email         = email;
        u.nickname      = DEFAULT_NICKNAME_PREFIX;   // 임시 — id 발급 후 「사용자{id}」
        return u;
    }

    /** 「사용자{id}」. 이미 다른 닉네임이면 건드리지 않는다. */
    public void assignDefaultNickname() {
        if (this.id != null && DEFAULT_NICKNAME_PREFIX.equals(this.nickname)) {
            this.nickname = DEFAULT_NICKNAME_PREFIX + this.id;
        }
    }

    // ── 수정 ─────────────────────────────────────────────────────────────────────

    /** PATCH /users/me — 수정 가능한 프로필은 이메일·닉네임만 (name·gender·birthDate 는 미수집). */
    public void updateProfile(String email, String nickname) {
        if (email != null)    this.email    = email;
        if (nickname != null) this.nickname = nickname;
    }

    public void deactivate() {
        this.isActive  = false;
        this.deletedAt = LocalDateTime.now();
        this.oauthId   = "DELETED_" + this.id;  // unique 충돌 방지 (재가입 허용)
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
