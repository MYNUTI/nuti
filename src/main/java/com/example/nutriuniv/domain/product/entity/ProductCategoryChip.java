package com.example.nutriuniv.domain.product.entity;

import com.example.nutriuniv.domain.category.entity.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 같이 보이는 칩 (v3 DATA_005) — 소속 중분류(products.category_id) 말고 최대 2개 칩에 더 나온다.
 * 값은 데이터 팀 적재 파일에서 온다. 분류 목록(categories)과 연결 테이블로 두어 없는 코드가 들어오지 못하게 한다.
 */
@Entity
@Table(name = "product_category_chips",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_category_chips", columnNames = {"product_id", "category_id"}),
        indexes = @Index(name = "idx_product_category_chips_category", columnList = "category_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategoryChip {

    public static final int MAX_PER_PRODUCT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public static ProductCategoryChip of(Product product, Category category) {
        ProductCategoryChip c = new ProductCategoryChip();
        c.product = product;
        c.category = category;
        return c;
    }
}
