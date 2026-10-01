package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.common.util.SearchNormalizer;
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

    // 검색 색인 (기능명세서 6.1·6.2) — 저장 시 콜백이 name 에서 만든다. 공백·특수문자 제거 소문자 / 초성. GIN(trgm) 인덱스는 db/manual/06_search.sql
    @Column(name = "name_normalized", length = 255)
    private String nameNormalized;

    @Column(name = "name_chosung", length = 255)
    private String nameChosung;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    // 수동 수정 보호 (기능명세서 10.4 「수정한 값이 다음 적재 때 덮어쓰이지 않게」) — TRUE 면 엑셀 재적재가 이 제품 행을 건너뛴다.
    // 관리자 상품·영양성분 수정과 제보 처리 완료(DONE)가 켠다. 해제는 PATCH /admin/products/{id} 의 manuallyCorrected=false
    @Column(name = "manually_corrected", nullable = false, columnDefinition = "boolean default false")
    private boolean manuallyCorrected = false;

    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

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

    /** 저장·수정 때마다 검색 색인 컬럼을 이름에서 다시 만든다 — 저장할 때와 찾을 때 같은 함수(SearchNormalizer). */
    @PrePersist
    @PreUpdate
    private void refreshSearchIndex() {
        this.nameNormalized = SearchNormalizer.normalize(this.name);
        this.nameChosung = SearchNormalizer.chosung(this.name);
    }

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

    /** 관리자가 손으로 고쳤다 — 다음 엑셀 적재에서 이 행을 건너뛴다. */
    public void markManuallyCorrected() {
        this.manuallyCorrected = true;
        this.correctedAt = LocalDateTime.now();
    }

    /** 보호 해제 — 다음 적재가 다시 덮어쓴다. */
    public void clearManualCorrection() {
        this.manuallyCorrected = false;
        this.correctedAt = null;
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