package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByName(String name);

    // 바코드 스캔 조회 (3.1) — 13자리 정규화 값으로만 찾는다
    Optional<Product> findByBarcode(String barcode);

    // 바코드 중복 검사 — 다른 제품이 같은 바코드를 갖는가
    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    // 부팅 시 status 백필 필요 여부
    long countByStatusIsNull();

    // 브랜드에 상품 존재 시 삭제 차단
    boolean existsByBrandAndIsActiveTrue(Brand brand);

    // 카테고리에 상품 존재 시 삭제 차단
    boolean existsByCategoryIdAndIsActiveTrue(Long categoryId);

    // 카테고리별 브랜드 목록 + 상품 수 (GET /categories/{id}/brands)
    // depth1 → depth2 → depth3 계층 전체 커버
    @Query("""
        SELECT p.brand, COUNT(p)
        FROM Product p
        WHERE p.isActive = true
          AND p.brand IS NOT NULL
          AND p.category.id IN (
              SELECT c.id FROM Category c
              WHERE c.id = :categoryId
                 OR c.parent.id = :categoryId
                 OR c.parent.id IN (
                     SELECT c2.id FROM Category c2
                     WHERE c2.parent.id = :categoryId
                 )
          )
        GROUP BY p.brand
    """)
    List<Object[]> findBrandCountByCategoryId(@Param("categoryId") Long categoryId);

    // 전체 브랜드별 상품 수 (GET /brands)
    @Query("SELECT p.brand, COUNT(p) FROM Product p " +
            "WHERE p.isActive = true AND p.brand IS NOT NULL " +
            "GROUP BY p.brand")
    List<Object[]> findBrandCountAll();

    // 대시보드용
    long countByIsActiveTrue();

    @Query("SELECT AVG(p.nutritionScore) FROM Product p WHERE p.isActive = true")
    java.math.BigDecimal findAvgNutritionScore();

    @Query("SELECT MAX(p.updatedAt) FROM Product p WHERE p.isActive = true")
    java.time.LocalDateTime findLatestUpdatedAt();

    @EntityGraph(attributePaths = {"coupangLink"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    // 추천 API — CF/콘텐츠 결과 ID 목록으로 상품 일괄 조회
    @EntityGraph(attributePaths = {"brand", "category"})
    List<Product> findByIdInAndIsActiveTrue(List<Long> ids);

    // 추천 API — 인기순 폴백
    @EntityGraph(attributePaths = {"brand", "category"})
    List<Product> findTopByIsActiveTrueOrderByViewCountDesc(Pageable pageable);
}