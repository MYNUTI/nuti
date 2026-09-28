package com.example.nutriuniv.domain.consent.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 동의 1건에 포함된 항목 코드(예: PERSONAL_INFO, AGE_OVER_14). 코드 목록은 팀 확정 사항 — 서버는 문자열로만 보관. */
@Entity
@Table(name = "consent_items", indexes = @Index(name = "idx_consent_items_consent", columnList = "consent_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_id", nullable = false)
    private Consent consent;

    @Column(name = "item_code", nullable = false, length = 40)
    private String itemCode;

    static ConsentItem of(Consent consent, String itemCode) {
        ConsentItem i = new ConsentItem();
        i.consent = consent;
        i.itemCode = itemCode;
        return i;
    }
}
