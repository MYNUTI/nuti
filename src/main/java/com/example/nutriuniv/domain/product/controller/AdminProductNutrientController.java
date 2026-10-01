package com.example.nutriuniv.domain.product.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.product.dto.AdminProductChangeLogResponse;
import com.example.nutriuniv.domain.product.dto.AdminProductNutrientUpdateRequest;
import com.example.nutriuniv.domain.product.dto.AdminProductNutrientUpdateResponse;
import com.example.nutriuniv.domain.product.service.ProductNutrientAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Product Nutrient", description = "관리자 영양성분 수정·수정 이력 (기능명세서 10.4)")
@RestController
@RequiredArgsConstructor
public class AdminProductNutrientController {

    private final ProductNutrientAdminService productNutrientAdminService;

    // PATCH /admin/products/{productId}/nutrients
    @Operation(summary = "영양성분 수정 (명세 10.4)",
            description = "전달된 필드만 수정(null 유지). 저장 후 분석 완료 판정을 갱신하고 그 제품의 등급 9슬롯을 즉시 재계산합니다(백분위는 다음 전량 배치). " +
                    "재적재 보호(manuallyCorrected)를 켜고 수정 이력에 「필드: 전 → 후」를 남깁니다. reportId 를 주면 제보와 연결. 음수 400, 바뀐 필드 없음 400.")
    @PatchMapping("/admin/products/{productId}/nutrients")
    public ResponseEntity<CommonResponse<AdminProductNutrientUpdateResponse>> update(
            Actor actor,
            @Parameter(description = "제품 ID") @PathVariable Long productId,
            @RequestBody AdminProductNutrientUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(productNutrientAdminService.update(productId, request, actor.userId())));
    }

    // GET /admin/products/{productId}/change-logs
    @Operation(summary = "수정 이력",
            description = "관리자 상품 수정·영양성분 수정·제보 처리 완료가 남긴 이력(최근순)과 현재 재적재 보호 상태.")
    @GetMapping("/admin/products/{productId}/change-logs")
    public ResponseEntity<CommonResponse<AdminProductChangeLogResponse>> changeLogs(
            @Parameter(description = "제품 ID") @PathVariable Long productId) {
        return ResponseEntity.ok(CommonResponse.success(productNutrientAdminService.changeLogs(productId)));
    }
}
