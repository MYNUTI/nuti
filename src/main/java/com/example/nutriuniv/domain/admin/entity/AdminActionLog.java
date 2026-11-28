package com.example.nutriuniv.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 운영 처리 이력 (v3 ADM_007) — 운영 기능(/admin/**)을 바꾸는 호출마다 누가 · 언제 · 무엇을(메서드 · 경로 · 대상) 했고 결과(HTTP 상태)가 어땠는지.
 * 조회(GET)는 남기지 않는다. 요청 본문은 남기지 않는다(개인정보 · 파일).
 */
@Entity
@Table(name = "admin_action_logs",
        indexes = {
                @Index(name = "idx_admin_action_logs_admin", columnList = "admin_user_id, created_at"),
                @Index(name = "idx_admin_action_logs_created", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 300)
    private String path;

    /** 경로 변수(대상 id 등) — 「productId=12, reportId=3」. 없으면 null. */
    @Column(length = 300)
    private String target;

    @Column(nullable = false)
    private int status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AdminActionLog of(Long adminUserId, String method, String path, String target, int status) {
        AdminActionLog l = new AdminActionLog();
        l.adminUserId = adminUserId;
        l.method = method;
        l.path = cut(path, 300);
        l.target = cut(target, 300);
        l.status = status;
        l.createdAt = LocalDateTime.now();
        return l;
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
