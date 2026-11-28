package com.example.nutriuniv.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기기 기록 삭제 이력 (v3 ADM_006) — 누가 어느 기기 기록을 지웠는지, 요청 시각 · 처리 시각(created_at) · 결과(지운 행 · 끊은 행).
 * 기기 ID 원문 대신 SHA-256 해시를 남긴다.
 */
@Entity
@Table(name = "device_deletion_logs",
        indexes = @Index(name = "idx_device_deletion_logs_hash", columnList = "anonymous_id_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anonymous_id_hash", nullable = false, length = 64)
    private String anonymousIdHash;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "deleted_rows", nullable = false)
    private int deletedRows;

    @Column(name = "unlinked_rows", nullable = false)
    private int unlinkedRows;

    @Column(length = 200)
    private String reason;

    /** 사용자가 삭제를 요청한 시각 (메일 받은 시각 등 — 운영자가 넣는다). 모르면 null. */
    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    /** 처리 시각. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static DeviceDeletionLog of(String anonymousIdHash, Long adminUserId, int deletedRows, int unlinkedRows, String reason,
                                      LocalDateTime requestedAt) {
        DeviceDeletionLog l = new DeviceDeletionLog();
        l.anonymousIdHash = anonymousIdHash;
        l.adminUserId = adminUserId;
        l.deletedRows = deletedRows;
        l.unlinkedRows = unlinkedRows;
        l.reason = reason;
        l.requestedAt = requestedAt;
        l.createdAt = LocalDateTime.now();
        return l;
    }
}
