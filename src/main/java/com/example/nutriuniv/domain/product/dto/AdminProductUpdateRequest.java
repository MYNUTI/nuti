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
    private Boolean manuallyCorrected; // 재적재 보호 플래그 직접 지정 (10.4). null 이면 자동(데이터 필드를 고치면 켜짐). false 로 해제
    private String memo;            // 수정 이력(product_change_logs)에 남길 메모 (선택)
}