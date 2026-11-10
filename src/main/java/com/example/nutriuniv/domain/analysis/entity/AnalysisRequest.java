package com.example.nutriuniv.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 분석 요청 (v3 CTRB · ADM_002).
 * <ul>
 *   <li>바코드 · 검색어 · 제품 ID 요청은 식별값당 1행 — 다른 기기가 다시 요청하면 request_count 만 +1 (같은 기기는 한 번만 센다)</li>
 *   <li>제품명 요청(이름 필수 · 브랜드 선택)은 늘 새 행. 어디서 왔는지(origin_barcode · origin_keyword)를 함께 남긴다</li>
 * </ul>
 * 식별값 컬럼 3개는 각각 UNIQUE — PostgreSQL 은 NULL 을 서로 다른 값으로 보므로 제품명 요청 행은 충돌하지 않는다.
 * 완료 후에도 행을 지우지 않고 DONE 으로만 바꾼다.
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

    public static final int NAME_MAX_LENGTH = 100;
    public static final int BRAND_MAX_LENGTH = 50;

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

    @Column(name = "product_name", length = NAME_MAX_LENGTH)
    private String productName;             // 제품명 요청 — 이름

    @Column(name = "brand_name", length = BRAND_MAX_LENGTH)
    private String brandName;               // 제품명 요청 — 브랜드(선택)

    @Column(name = "origin_barcode", length = 13)
    private String originBarcode;           // 제품명 요청 — 못 찾은 바코드에서 왔으면

    @Column(name = "origin_keyword", length = 30)
    private String originKeyword;           // 제품명 요청 — 결과 없는 검색어에서 왔으면

    @Column(name = "request_count", nullable = false)
    private int requestCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AnalysisRequestStatus status = AnalysisRequestStatus.ANALYZING;

    @Column(name = "first_requested_at", nullable = false)
    private LocalDateTime firstRequestedAt;

    @Column(name = "last_requested_at", nullable = false)
    private LocalDateTime lastRequestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private Long processedBy;               // 관리자 user id

    /** 제품명 요청 — 늘 새 행. */
    public static AnalysisRequest nameRequest(String productName, String brandName, String originBarcode, String originKeyword) {
        AnalysisRequest r = new AnalysisRequest();
        r.type = AnalysisRequestType.NEW_PRODUCT;
        r.productName = productName;
        r.brandName = brandName;
        r.originBarcode = originBarcode;
        r.originKeyword = originKeyword;
        r.requestCount = 1;
        r.status = AnalysisRequestStatus.ANALYZING;
        r.firstRequestedAt = LocalDateTime.now();
        r.lastRequestedAt = r.firstRequestedAt;
        return r;
    }

    public boolean isNameRequest() {
        return productName != null;
    }

    /** 요청 방식 — BARCODE | KEYWORD | NAME | PRODUCT. */
    public String kind() {
        if (productName != null) return "NAME";
        if (barcode != null) return "BARCODE";
        if (keyword != null) return "KEYWORD";
        return "PRODUCT";
    }

    /** 식별값 한 줄 — 관리자 목록용. */
    public String identifier() {
        if (productName != null) return brandName == null ? productName : brandName + " " + productName;
        if (barcode != null) return barcode;
        if (keyword != null) return keyword;
        return productId == null ? null : String.valueOf(productId);
    }

    /** 관리자 상태 변경. DONE·FAILED 로 바꾸면 처리 시각·처리자를 남긴다. */
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
