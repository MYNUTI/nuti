package com.example.nutriuniv.domain.support.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 문의 (API 명세 /support/inquiries — 「신규(결정 필요)」: 카카오 채널로 대체하면 이 표는 쓰지 않는다).
 * 비로그인도 가능, 연락처 선택. 하루 3건 한도 계산을 위해 소유자 외에 세션 ID 도 남긴다.
 */
@Entity
@Table(name = "support_inquiries",
        indexes = @Index(name = "idx_support_inquiries_created", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportInquiry {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(length = 20)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(nullable = false, length = 10)
    private String status = "RECEIVED";     // RECEIVED · ANSWERED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    public static SupportInquiry create(Owner owner, String sessionId, String category, String content, String contactEmail) {
        SupportInquiry i = new SupportInquiry();
        i.userId       = owner == null ? null : owner.userId();
        i.anonymousId  = owner == null ? null : owner.anonymousId();
        i.sessionId    = sessionId;
        i.category     = category;
        i.content      = content;
        i.contactEmail = contactEmail;
        i.status       = "RECEIVED";
        i.createdAt    = LocalDateTime.now();
        return i;
    }
}
