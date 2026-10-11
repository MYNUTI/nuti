package com.example.nutriuniv.domain.product.dto;

import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/** 결과 화면·스캔 응답의 product 블록 (API 명세: productId·name·brandName·imageUrl·servingSize·sourceNote). */
@Getter
@Builder
public class ProductSummary {

    private Long productId;
    private String name;
    private String brandName;
    private String imageUrl;
    private String servingSize;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sourceNote;      // 출처 문구 (5.1) — 결과 화면에서만

    public static ProductSummary of(Product product, ProductNutrient nutrient, String sourceNote) {
        return ProductSummary.builder()
                .productId(product.getId())
                .name(product.getName())
                .brandName(product.getBrand() == null ? null : product.getBrand().getName())
                .imageUrl(product.getImageUrl())
                .servingSize(nutrient == null ? null : nutrient.getServingSize())
                .sourceNote(sourceNote)
                .build();
    }
}
