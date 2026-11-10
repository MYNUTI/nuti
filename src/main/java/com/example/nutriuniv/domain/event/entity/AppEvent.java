package com.example.nutriuniv.domain.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 앱 이벤트 (v3 이벤트 목록) — 표 정의용 엔티티. 쓰기는 EventService 가 JdbcTemplate 로 한 번에(props 는 jsonb).
 * 몸 정보(키 · 몸무게 · 나이 · 성별 · 출생 연도)는 props 에 남기지 않는다.
 */
@Entity
@Table(name = "app_events",
        indexes = {
                @Index(name = "idx_app_events_name_occurred", columnList = "name, occurred_at"),
                @Index(name = "idx_app_events_anonymous", columnList = "anonymous_id"),
                @Index(name = "idx_app_events_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(length = 50)
    private String screen;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(columnDefinition = "jsonb")
    private String props;
}
