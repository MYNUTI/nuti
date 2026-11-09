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

    // 소속 중분류 (v3 DATA_005) — 적재 파일의 category 코드. 영유아식·환자식·술·미분류는 비어 있다(검색·스캔에만 나온다)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
    private ProductNutrient productNutrient;

    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
    private CoupangLink coupangLink;

    // 적재 기준 키 (v3 DATA_000) — 데이터 팀 master_id. 같은 키면 같은 제품으로 갱신하고, 이름으로 합치지 않는다. 운영자가 만든 제품은 비어 있을 수 있다
    @Column(name = "master_id", length = 40, unique = true)
    private String masterId;

    // 품목제조보고번호 — 같이 저장만 한다(크롤링으로만 들어온 제품은 없을 수 있다)
    @Column(name = "report_no", length = 20)
    private String reportNo;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "maker", length = 255)
    private String maker;

    @Column(name = "ingredients", columnDefinition = "text")
    private String ingredients;

    // 생산 중단 표시 (적재 파일 is_discontinued) — 쿠팡 확인 결과와 합쳐 상태 5종을 정한다(ProductState)
    @Column(name = "is_discontinued", nullable = false, columnDefinition = "boolean default false")
    private boolean discontinued = false;

    // 적재 파일 content_hash — 이전 적재와 같으면 그 행은 건너뛴다
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "nutrition_score", precision = 5, scale = 2)
    private BigDecimal nutritionScore;

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

    // 수동 수정 보호 (기능명세서 10.4 「수정한 값이 다음 적재 때 덮어쓰이지 않게」) — TRUE 면 재적재가 이 제품 행을 건너뛴다.
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

    /** 적재 파일로 새로 만든다 (v3 DATA_000). */
    public static Product loaded(String masterId, String name) {
        Product p = new Product();
        p.masterId = masterId;
        p.name = name;
        return p;
    }

    /** 적재 파일 한 제품의 값을 그대로 덮는다 — 수동 수정 보호 제품은 호출하지 않는다. */
    public void applyLoad(String name, Category category, Brand brand, String reportNo, String source,
                          String maker, String ingredients, boolean discontinued, String contentHash) {
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.reportNo = reportNo;
        this.source = source;
        this.maker = maker;
        this.ingredients = ingredients;
        this.discontinued = discontinued;
        this.contentHash = contentHash;
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

    public void updateStatus(ProductStatus status) {
        this.status = status;
    }

    /** 관리자가 손으로 고쳤다 — 다음 적재에서 이 행을 건너뛴다. */
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