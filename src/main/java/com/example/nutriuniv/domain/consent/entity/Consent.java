package com.example.nutriuniv.domain.consent.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 동의 원장 — UPDATE·DELETE 없이 행만 쌓는다(재동의·철회 = 새 행). 어느 항목에 언제 동의했는지 재구성 가능.
 * 소유자는 user_id XOR anonymous_id (DB CHECK 제약은 db/manual SQL 에서).
 * 접속 환경 증빙으로 IP 는 원본 대신 SHA-256 해시만 둔다(기능명세서 1.2).
 */
@Entity
@Table(name = "consents",
        indexes = {
                @Index(name = "idx_consents_user", columnList = "user_id, consent_type, created_at"),
                @Index(name = "idx_consents_anon", columnList = "anonymous_id, consent_type, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 10)
    private ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConsentAction action;

    @Column(name = "policy_version", nullable = false, length = 20)
    private String policyVersion;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "consent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConsentItem> items = new ArrayList<>();

    // ── 생성 ─────────────────────────────────────────────────────────────────────

    public static Consent agree(Owner owner, ConsentType type, String policyVersion,
                                Collection<String> itemCodes, String ipHash, String userAgent) {
        Consent c = base(owner, type, ConsentAction.AGREE, policyVersion, ipHash, userAgent);
        for (String code : itemCodes) {
            c.items.add(ConsentItem.of(c, code));
        }
        return c;
    }

    public static Consent revoke(Owner owner, ConsentType type, String policyVersion,
                                 String ipHash, String userAgent) {
        return base(owner, type, ConsentAction.REVOKE, policyVersion, ipHash, userAgent);
    }

    private static Consent base(Owner owner, ConsentType type, ConsentAction action, String policyVersion,
                                String ipHash, String userAgent) {
        Consent c = new Consent();
        c.userId = owner.userId();
        c.anonymousId = owner.anonymousId();
        c.consentType = type;
        c.action = action;
        c.policyVersion = policyVersion;
        c.ipHash = ipHash;
        c.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        c.createdAt = LocalDateTime.now();
        return c;
    }
}
