package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.brand.repository.BrandRepository;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductExcelRowService {

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    /**
     * 행 하나를 독립 트랜잭션으로 처리.
     * 실패해도 다른 행 트랜잭션에 영향 없음.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRow(Map<String, String> values,
                           Map<String, Brand> brandCache,
                           Map<String, Category> catCache) {

        String productName = values.getOrDefault("name", "").trim();
        String brandName   = values.getOrDefault("brand", "").trim();
        String depth1Name  = values.getOrDefault("categoryDepth1", "").trim();
        String depth2Name  = values.getOrDefault("categoryDepth2", "").trim();

        if (productName.isEmpty()) throw new CustomException(ErrorCode.BAD_REQUEST, "제품명이 비어있습니다.");
        if (brandName.isEmpty())   throw new CustomException(ErrorCode.BAD_REQUEST, "브랜드가 비어있습니다.");
        if (depth1Name.isEmpty())  throw new CustomException(ErrorCode.BAD_REQUEST, "대분류가 비어있습니다.");
        if (depth2Name.isEmpty())  throw new CustomException(ErrorCode.BAD_REQUEST, "중분류가 비어있습니다.");

        Brand brand = brandCache.computeIfAbsent(brandName, n ->
                brandRepository.findByName(n).orElseGet(() -> brandRepository.save(Brand.create(n))));

        Category depth1 = catCache.computeIfAbsent(depth1Name, n ->
                categoryRepository.findByNameAndDepth(n, 1)
                        .orElseGet(() -> categoryRepository.save(Category.createDepth1(n))));

        String depth2Key = depth1Name + "::" + depth2Name;
        Category depth2 = catCache.computeIfAbsent(depth2Key, k ->
                categoryRepository.findByNameAndDepthAndParent(depth2Name, 2, depth1)
                        .orElseGet(() -> categoryRepository.save(Category.createDepth2(depth2Name, depth1))));

        Product product = productRepository.findByName(productName)
                .orElseGet(() -> productRepository.save(Product.create(productName, depth2, brand)));
        product.update(depth2, brand);

        // 바코드 (선택 열) — 13자리 정규화 저장 (3.1). 형식·체크섬 오류나 다른 제품과 중복이면 행은 살리고 바코드만 건너뛴다
        String rawBarcode = values.getOrDefault("barcode", "").trim();
        if (!rawBarcode.isEmpty()) {
            BarcodeNormalizer.tryNormalize(rawBarcode).ifPresentOrElse(code -> {
                if (productRepository.existsByBarcodeAndIdNot(code, product.getId())) {
                    log.warn("[ExcelUpload] 바코드 중복으로 건너뜀 — product={} barcode={}", product.getName(), code);
                } else {
                    product.updateBarcode(code);
                }
            }, () -> log.warn("[ExcelUpload] 바코드 형식/체크섬 오류로 건너뜀 — product={} raw={}", product.getName(), rawBarcode));
        }

        ProductNutrient nutrient = productNutrientRepository.findById(product.getId())
                .orElseGet(() -> ProductNutrient.create(product));

        nutrient.update(
                values.getOrDefault("servingSize", ""),
                parseNutrient(values.get("calories")),
                parseNutrient(values.get("carbohydrate")),
                parseNutrient(values.get("sugar")),
                parseNutrient(values.get("protein")),
                parseNutrient(values.get("fat")),
                parseNutrient(values.get("saturatedFat")),
                parseNutrient(values.get("transFat")),
                parseNutrient(values.get("cholesterol")),
                parseNutrient(values.get("sodium")),
                parseNutrient(values.get("fiber")),
                parseNutrient(values.get("caloriesPer100g")),
                parseNutrient(values.get("carbohydratePer100g")),
                parseNutrient(values.get("sugarPer100g")),
                parseNutrient(values.get("proteinPer100g")),
                parseNutrient(values.get("fatPer100g")),
                parseNutrient(values.get("saturatedFatPer100g")),
                parseNutrient(values.get("transFatPer100g")),
                parseNutrient(values.get("cholesterolPer100g")),
                parseNutrient(values.get("sodiumPer100g")),
                parseNutrient(values.get("fiberPer100g"))
        );
        productNutrientRepository.save(nutrient);
        product.updateStatus(ProductStatus.of(nutrient));   // 분석 완료 판정(4.1) — 등급 배치·검색 정렬이 읽는다
    }

    /** 빈 셀·숫자 아님은 결측(null) — 0 으로 채우면 「영양정보 부족」 판정(4.1)이 불가능해진다. */
    private BigDecimal parseNutrient(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String numOnly = raw.replaceAll("[^0-9.]", "").trim();
        if (numOnly.isEmpty()) return null;
        try {
            return new BigDecimal(numOnly);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
