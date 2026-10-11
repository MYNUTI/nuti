package com.example.nutriuniv.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 제품 데이터 수정 이력 (기능명세서 10.4 「수정하면 무엇을 언제 고쳤는지 남긴다」).
 * 관리자 상품 수정(PATCH /admin/products/{id})·영양성분 수정(PATCH /admin/products/{id}/nutrients)·제보 처리 완료가 한 줄씩 남긴다.
 * summary 는 「name: A → B / brand: 1 → 2」 같은 사람이 읽는 문장.
 */
@Entity
@Table(name = "product_change_logs",
        indexes = @Index(name = "idx_product_change_logs_product", columnList = "product_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductChangeLog {

    public enum Source { ADMIN_UPDATE, ADMIN_NUTRIENT_UPDATE, REPORT_DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "report_id")
    private Long reportId;                  // 제보 처리에서 비롯된 수정이면

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Source source;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ProductChangeLog create(Long productId, Long reportId, Long adminUserId, Source source, String summary) {
        ProductChangeLog l = new ProductChangeLog();
        l.productId   = productId;
        l.reportId    = reportId;
        l.adminUserId = adminUserId;
        l.source      = source;
        l.summary     = summary;
        l.createdAt   = LocalDateTime.now();
        return l;
    }
}
