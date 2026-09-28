package com.example.nutriuniv.domain.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class AdminProductUpdateRequest {

    private String name;
    private Long categoryId;
    private Long brandId;
    private Boolean isActive;
    private String imageUrl;
    private BigDecimal nutritionScore;
    private String barcode;         // 8·12·13·14자리 → 13자리 정규화 저장. 빈 문자열이면 제거, null 이면 유지
}