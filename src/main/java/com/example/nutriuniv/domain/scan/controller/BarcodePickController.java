package com.example.nutriuniv.domain.scan.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.scan.dto.BarcodePickResponse;
import com.example.nutriuniv.domain.scan.service.BarcodePickService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Scan", description = "바코드 확인 · 선택 (v3 SCAN_008 · SCAN_009)")
@RestController
@RequiredArgsConstructor
public class BarcodePickController {

    private final BarcodePickService pickService;

    public record PickRequest(Long productId, String kind) {}

    // POST /products/barcode/{barcode}/picks
    @Operation(summary = "바코드 제품 고르기",
            description = "kind=CONFIRM: 「이 제품인가요？」에 맞아요(또는 아니에요 → 검색에서 고른 제품). " +
                    "kind=MULTIPLE: 바코드에 제품이 여러 개일 때 고른 제품. 기기(계정)마다 기억해 다음 스캔부터 바로 결과를 엽니다. " +
                    "서로 다른 2명이 같은 제품을 고르면 운영 「확인 대기」에 올리고, CONFIRM 이면 바코드를 그 제품에 바로 잇습니다(linked=true). " +
                    "message 는 아래 알림 문구. 비로그인 가능(기기 ID 가 없으면 기억하지 않음).")
    @PostMapping("/products/barcode/{barcode}/picks")
    public ResponseEntity<CommonResponse<BarcodePickResponse>> pick(
            Actor actor,
            @Parameter(description = "바코드 (8 · 12 · 13 · 14자리)") @PathVariable String barcode,
            @RequestBody PickRequest request) {
        if (request == null || request.productId() == null) {
            throw new com.example.nutriuniv.common.exception.CustomException(
                    com.example.nutriuniv.common.exception.ErrorCode.BAD_REQUEST, "productId 가 필요합니다.");
        }
        return ResponseEntity.ok(CommonResponse.success(pickService.pick(barcode, request.productId(), request.kind(), actor)));
    }
}
