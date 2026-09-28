package com.example.nutriuniv.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 분석 대기 항목 (기능명세서 10.2). 식별값(바코드·검색어·제품 ID)당 1행 — 같은 식별값 재접수는 request_count 만 +1 (새 행 없음).
 * 스캔 404·영양정보 부족 자동 등록과 사용자 수동 접수가 같은 표를 쓴다. 완료 후에도 행을 지우지 않고 DONE 으로만 바꾼다.
 * <p>식별값 컬럼 3개는 각각 UNIQUE — PostgreSQL 은 NULL 을 서로 다른 값으로 보므로 nullable 컬럼의 UNIQUE 가 그대로 부분 유니크 역할을 한다.
 * 쓰기(upsert)는 AnalysisRequestService 가 INSERT … ON CONFLICT 로 원자적으로 한다.
 */
@Entity
@Table(name = "analysis_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_analysis_requests_barcode", columnNames = "barcode"),
                @UniqueConstraint(name = "uk_analysis_requests_keyword", columnNames = "keyword"),
                @UniqueConstraint(name = "uk_analysis_requests_product", columnNames = "product_id")
        },
        indexes = @Index(name = "idx_analysis_requests_status_count", columnList = "status, request_count"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisRequestType type;

    @Column(length = 13)
    private String barcode;                 // NEW_PRODUCT — 13자리 정규화 값

    @Column(length = 30)
    private String keyword;                 // NEW_PRODUCT — 검색어(정규화: trim·공백 축약)

    @Column(name = "product_id")
    private Long productId;                 // NUTRITION_FILL

    @Column(name = "request_count", nullable = false)
    private int requestCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AnalysisRequestStatus status = AnalysisRequestStatus.WAITING;

    @Column(name = "first_requested_at", nullable = false)
    private LocalDateTime firstRequestedAt;

    @Column(name = "last_requested_at", nullable = false)
    private LocalDateTime lastRequestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;               // 관리자 user id

    /** 식별값 한 줄 — 관리자 목록용. */
    public String identifier() {
        if (barcode != null) return barcode;
        if (keyword != null) return keyword;
        return productId == null ? null : String.valueOf(productId);
    }

    /** 관리자 상태 변경 (10.2). DONE·HOLD 로 바꾸면 처리 시각·처리자를 남긴다. */
    public void changeStatus(AnalysisRequestStatus next, Long adminUserId) {
        this.status = next;
        if (next.isTerminal()) {
            this.processedAt = LocalDateTime.now();
            this.processedBy = adminUserId;
        } else {
            this.processedAt = null;
            this.processedBy = null;
        }
    }
}
