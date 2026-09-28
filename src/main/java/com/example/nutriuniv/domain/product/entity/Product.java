package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
    private ProductNutrient productNutrient;

    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
    private CoupangLink coupangLink;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "nutrition_score", precision = 5, scale = 2)
    private BigDecimal nutritionScore;

    // 바코드 — 13자리 정규화 값만 저장 (기능명세서 3.1). 저장·조회 모두 BarcodeNormalizer 를 거친다. 없으면 null
    @Column(length = 13, unique = true)
    private String barcode;

    // 분석 완료 판정 (4.1): 판정 7종 *_per_100g 전부 있으면 ANALYZED, 아니면 INSUFFICIENT. 영양정보 저장 시와 등급 배치가 갱신
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductStatus status;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // pgvector 타입 (10차원). Python vectorize.py가 직접 write한다.
    // JPA INSERT/UPDATE에서 제외 — Python만 이 컬럼을 관리함
    @Column(name = "nutrient_vector", columnDefinition = "vector(10)", insertable = false, updatable = false)
    private String nutrientVector;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Product create(String name, Category category, Brand brand) {
        Product p = new Product();
        p.name = name;
        p.category = category;
        p.brand = brand;
        return p;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void update(Category category, Brand brand) {
        this.category = category;
        this.brand = brand;
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /** 정규화된 13자리(BarcodeNormalizer.normalize 결과)만 넣는다. null 이면 제거. */
    public void updateBarcode(String normalized13) {
        this.barcode = normalized13;
    }

    public void updateStatus(ProductStatus status) {
        this.status = status;
    }

    public void update(String name, Category category, Brand brand,
                       String imageUrl, BigDecimal nutritionScore) {
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.nutritionScore = nutritionScore;
    }
}