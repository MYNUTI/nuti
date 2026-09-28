package com.example.nutriuniv.domain.analysis.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 접수 개별 기록 — 일일 한도(검색어 접수 하루 5건 → 429) 계산과 마이 화면 기여 집계(recordCount·contribution)용.
 * 소유자는 user_id XOR anonymous_id, 동의 전 사용자는 session_id 만. 셋 다 없으면 한도를 셀 수 없어 기록만 남긴다.
 */
@Entity
@Table(name = "analysis_request_events",
        indexes = {
                @Index(name = "idx_analysis_request_events_request", columnList = "request_id"),
                @Index(name = "idx_analysis_request_events_created", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisRequestChannel channel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AnalysisRequestEvent create(Long requestId, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        AnalysisRequestEvent e = new AnalysisRequestEvent();
        e.requestId   = requestId;
        e.userId      = owner == null ? null : owner.userId();
        e.anonymousId = owner == null ? null : owner.anonymousId();
        e.sessionId   = sessionId;
        e.channel     = channel;
        e.createdAt   = LocalDateTime.now();
        return e;
    }
}
