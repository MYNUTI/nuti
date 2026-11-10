package com.example.nutriuniv.domain.consent.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 동의 기록 (v3 ACCT_004 · ACCT_007 · ONB_009) — 항목마다 한 줄, 추가만 하고 고치지 않는다(동의 · 철회 = 새 줄).
 * 지금 상태는 항목별 마지막 줄. 계정만 동의한다(기기 ID 는 동의 없이 쓴다). IP 는 원본 대신 SHA-256 해시.
 */
@Entity
@Table(name = "consent_records",
        indexes = @Index(name = "idx_consent_records_user_item", columnList = "user_id, item_code, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_code", nullable = false, length = 20)
    private ConsentItemCode itemCode;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "privacy_version", length = 20)
    private String privacyVersion;

    /** 동의문 판 — 몸 정보 · 목표 저장 동의문처럼 따로 판이 있는 항목. */
    @Column(name = "document_version", length = 20)
    private String documentVersion;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ConsentRecord of(Long userId, ConsentItemCode item, boolean agreed, String termsVersion, String privacyVersion,
                                   String documentVersion, String ipHash, String userAgent, LocalDateTime at) {
        ConsentRecord r = new ConsentRecord();
        r.userId = userId;
        r.itemCode = item;
        r.agreed = agreed;
        r.termsVersion = termsVersion;
        r.privacyVersion = privacyVersion;
        r.documentVersion = documentVersion;
        r.ipHash = ipHash;
        r.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        r.createdAt = at;
        return r;
    }
}
