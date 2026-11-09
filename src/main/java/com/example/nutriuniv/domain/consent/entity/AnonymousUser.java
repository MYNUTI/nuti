package com.example.nutriuniv.domain.consent.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기기 익명 ID 와 계정의 연결 기록.
 *
 * <p>v3(ACCT_001)부터 익명 ID 는 기기가 만들고 서버는 발급하지 않는다 — 그래서 모든 기기가 여기 있는 것이 아니라,
 * 로그인해서 기록을 계정으로 옮긴(ACCT_005) 기기만 한 줄 남는다. 같은 기기로 다시 옮기면 마지막 계정·시각으로 덮는다.
 * 소유자 판정(OwnerResolver)은 이 표를 보지 않는다.
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

    // 마지막으로 기록을 옮긴 계정
    @Column(name = "merged_user_id", nullable = false)
    private Long mergedUserId;

    @Column(name = "merged_at", nullable = false)
    private LocalDateTime mergedAt;

    // 처음 계정으로 옮긴 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AnonymousUser linked(String anonymousId, Long userId) {
        AnonymousUser a = new AnonymousUser();
        a.anonymousId = anonymousId;
        a.createdAt = LocalDateTime.now();
        a.markMerged(userId);
        return a;
    }

    public void markMerged(Long userId) {
        this.mergedUserId = userId;
        this.mergedAt = LocalDateTime.now();
    }
}
