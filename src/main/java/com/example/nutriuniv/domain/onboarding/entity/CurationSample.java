package com.example.nutriuniv.domain.onboarding.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 샘플 픽커 큐레이션 (기능명세서 2.2·10.1). 관리자가 지정한 제품을 먼저 쓰고 모자라면 조회수 상위로 채운다.
 * A등급 최소 20건 유지(재산출 전 A 3.1%라 자동으론 안 채워짐). 분석 완료 제품만 지정 가능. 제품이 비활성화되면 조회 시 자동 제외 + 경고.
 */
@Entity
@Table(name = "curation_samples",
        uniqueConstraints = @UniqueConstraint(name = "uk_curation_samples_product", columnNames = "product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurationSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CurationSample create(Long productId, int displayOrder, Long adminUserId) {
        CurationSample c = new CurationSample();
        c.productId = productId;
        c.displayOrder = displayOrder;
        c.isActive = true;
        c.createdBy = adminUserId;
        c.updatedAt = LocalDateTime.now();
        return c;
    }

    public void activate(int displayOrder, Long adminUserId) {
        this.isActive = true;
        this.displayOrder = displayOrder;
        this.createdBy = adminUserId;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }
}
