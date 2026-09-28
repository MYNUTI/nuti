package com.example.nutriuniv.domain.logging.entity;

import com.example.nutriuniv.domain.logging.dto.LogContext;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 스캔 실패 이벤트 (기능명세서 9.1 신규) — 원인별 분리 적재. IP 는 남기지 않는다(동의 전 사용자도 보내는 이벤트).
 * surface 는 클라 화면 식별자(예: ONBOARDING·HOME), 서버가 스스로 기록한 체크섬 실패는 SERVER.
 */
@Entity
@Table(name = "scan_event_logs",
        indexes = {
                @Index(name = "idx_scan_event_logs_result_created", columnList = "result, created_at"),
                @Index(name = "idx_scan_event_logs_session", columnList = "session_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(length = 10)
    private String cohort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScanEventResult result;

    @Column(length = 14)
    private String barcode;                 // NOT_IN_DATA·CHECKSUM_FAIL 일 때만

    @Column(length = 20)
    private String surface;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ScanEventLog create(LogContext ctx, ScanEventResult result, String barcode, String surface) {
        ScanEventLog e = new ScanEventLog();
        e.userId      = ctx.userId();
        e.anonymousId = ctx.anonymousId();
        e.sessionId   = ctx.sessionId();
        e.cohort      = ctx.cohort();
        e.result      = result;
        e.barcode     = barcode;
        e.surface     = surface;
        e.createdAt   = LocalDateTime.now();
        return e;
    }
}
