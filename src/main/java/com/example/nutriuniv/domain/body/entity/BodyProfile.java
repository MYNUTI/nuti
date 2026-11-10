package com.example.nutriuniv.domain.body.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 몸 정보 (v3 ACCT_010 · CORE_008) — 「몸 정보 수집·이용」에 동의한 계정만 저장한다.
 * 하루 필요 칼로리는 기기가 계산해 보낸다(2025 한국인 영양소 섭취기준 식). 서버는 그 값으로 칼로리 구간만 정한다.
 */
@Entity
@Table(name = "body_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_body_profiles_user", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BodyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_year", nullable = false)
    private int birthYear;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 15)
    private ActivityLevel activityLevel;

    @Column(name = "daily_kcal", nullable = false)
    private int dailyKcal;

    @Column(name = "kcal_band", nullable = false)
    private int kcalBand;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Gender { MALE, FEMALE }

    /** 활동량 4단계 (KDRI 신체활동 단계). */
    public enum ActivityLevel { SEDENTARY, LOW_ACTIVE, ACTIVE, VERY_ACTIVE }

    public static BodyProfile create(Long userId) {
        BodyProfile p = new BodyProfile();
        p.userId = userId;
        return p;
    }

    public void update(Gender gender, int birthYear, BigDecimal heightCm, BigDecimal weightKg,
                       ActivityLevel activityLevel, int dailyKcal, int kcalBand) {
        this.gender = gender;
        this.birthYear = birthYear;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
        this.dailyKcal = dailyKcal;
        this.kcalBand = kcalBand;
        this.updatedAt = LocalDateTime.now();
    }
}
