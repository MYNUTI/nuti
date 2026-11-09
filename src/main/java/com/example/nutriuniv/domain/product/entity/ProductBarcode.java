package com.example.nutriuniv.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 제품–바코드 (v3 DATA_000) — 제품 하나에 바코드 여러 개, 바코드 하나가 제품 여러 개에 붙을 수 있다(N:M).
 * 바코드는 13자리 정규화 값만 넣는다(BarcodeNormalizer) — 저장할 때와 스캔해서 찾을 때 같은 값이 나와야 한다.
 */
@Entity
@Table(name = "product_barcodes",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_barcodes_product_barcode", columnNames = {"product_id", "barcode"}),
        indexes = @Index(name = "idx_product_barcodes_barcode", columnList = "barcode"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 13)
    private String barcode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** @param normalized13 BarcodeNormalizer.normalize 결과 */
    public static ProductBarcode of(Product product, String normalized13) {
        ProductBarcode b = new ProductBarcode();
        b.product = product;
        b.barcode = normalized13;
        return b;
    }
}
