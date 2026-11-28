package com.example.nutriuniv.domain.scan.dto;

/**
 * POST /products/barcode/{barcode}/picks 출력 — linked: 이번 선택으로 바코드를 그 제품에 이었는지(2명째), message: 아래 알림 문구.
 */
public record BarcodePickResponse(String barcode, Long productId, boolean linked, String message) {}
