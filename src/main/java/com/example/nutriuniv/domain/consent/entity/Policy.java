package com.example.nutriuniv.domain.consent.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 약관·처리방침 버전 (GET /meta/policies, 동의 시 버전 검증).
 * 종류별로 is_current 인 행이 하나 — 새 버전 배포 = 새 행 추가 + 이전 행 is_current=false (db/manual SQL 또는 관리자).
 */
@Entity
@Table(name = "policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_policies_type_version", columnNames = {"policy_type", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 10)
    private PolicyType policyType;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent = false;
}
