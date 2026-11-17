package com.example.nutriuniv.domain.publicdata.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공공 API 호출 기록 — 한 번 부를 때마다 한 줄. 하루 · 시간당 · 낮 시간 호출 수를 세는 근거(PublicApiCallLogRepository).
 * 하루 수천 줄이라 30일 지난 줄은 지운다.
 */
@Entity
@Table(name = "public_api_call_logs",
        indexes = @Index(name = "idx_public_api_call_logs_api_time", columnList = "api, called_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicApiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String api;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @Column(length = 30)
    private String code;
}