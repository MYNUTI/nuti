package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 제품 오류 제보 (v3 CTRB_008). 비로그인도 가능. 고른 항목마다 한 행. 하루 10건 한도 계산을 위해 소유자 외에 세션 ID 도 남긴다.
 * v3 는 연락처(contact_email)를 받지 않는다 — 컬럼과 이미 있던 값은 그대로 두고 엔티티에서만 뺐다.
 * 처리 결과(메모·처리자·시각)는 여기, 실제 데이터 수정 이력은 product_change_logs.
 */
@Entity
@Table(name = "product_reports",
        indexes = {
                @Index(name = "idx_product_reports_status_created", columnList = "status, created_at"),
                @Index(name = "idx_product_reports_product", columnList = "product_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReport {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 13)
    private String barcode;                 // 어떤 바코드로 본 제품에서 제보했는지(선택)

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ReportStatus status = ReportStatus.RECEIVED;

    @Column(name = "admin_memo", columnDefinition = "text")
    private String adminMemo;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ProductReport create(Long productId, ReportType type, String content, String barcode, Owner owner, String sessionId) {
        ProductReport r = new ProductReport();
        r.productId    = productId;
        r.reportType   = type;
        r.content      = content;
        r.barcode      = barcode;
        r.userId       = owner == null ? null : owner.userId();
        r.anonymousId  = owner == null ? null : owner.anonymousId();
        r.sessionId    = sessionId;
        r.status       = ReportStatus.RECEIVED;
        r.createdAt    = LocalDateTime.now();
        return r;
    }

    /** 관리자 처리 — 상태·메모(둘 다 선택). 종결(DONE·REJECTED)이면 처리자·시각을 남긴다. */
    public void handle(ReportStatus next, String memo, Long adminUserId) {
        if (memo != null) this.adminMemo = memo;
        if (next != null) {
            this.status = next;
            if (next.isTerminal()) {
                this.handledBy = adminUserId;
                this.handledAt = LocalDateTime.now();
            } else {
                this.handledBy = null;
                this.handledAt = null;
            }
        }
    }
}
