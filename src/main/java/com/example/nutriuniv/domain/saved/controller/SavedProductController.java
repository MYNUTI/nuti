package com.example.nutriuniv.domain.saved.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.saved.dto.SaveResultResponse;
import com.example.nutriuniv.domain.saved.dto.SavedProductPageResponse;
import com.example.nutriuniv.domain.saved.service.SavedProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SavedProduct", description = "제품 저장 API (구 찜)")
@RestController
@RequiredArgsConstructor
public class SavedProductController {

    private final SavedProductService savedProductService;

    // GET /me/saved-products
    @Operation(summary = "저장 목록 (v3 COMP_006)",
            description = "Bearer 또는 X-Anonymous-Id(기기 ID) 필수(없으면 400 ANONYMOUS_ID_REQUIRED). 로그인·비로그인 같은 API, 소유자만 다릅니다. " +
                    "저장한 순(최근 먼저). 단종·생산 중단 제품도 상태 배지(product.state)로 남기고, 비활성 제품만 뺍니다. " +
                    "카드에 두 목표 점수·등급, 브랜드 줄, product.better(요청 goal 기준 TOP / AVAILABLE / NONE). page·size는 1부터.")
    @GetMapping("/me/saved-products")
    public ResponseEntity<CommonResponse<SavedProductPageResponse>> getSaved(
            Actor actor,
            @Parameter(description = "WEIGHT_LOSS(기본) | MUSCLE_GAIN") @RequestParam(required = false) String goal,
            @Parameter(description = "하루 칼로리 구간 (1500·2000·2500·3000)") @RequestParam(required = false) Integer kcalBand,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(CommonResponse.success(savedProductService.getSaved(actor, goal, kcalBand, page, size)));
    }

    // POST /me/saved-products/{productId}
    @Operation(summary = "제품 저장",
            description = "재저장은 200 멱등. 상한 200개 초과 409 SAVE_LIMIT_EXCEEDED, 없는 제품 404 PRODUCT_NOT_FOUND. " +
                    "로그인도 기기 ID 도 없으면 400 ANONYMOUS_ID_REQUIRED. 동의는 필요 없습니다.")
    @PostMapping("/me/saved-products/{productId}")
    public ResponseEntity<CommonResponse<SaveResultResponse>> save(
            Actor actor,
            @Parameter(description = "제품 ID") @PathVariable Long productId) {

        return ResponseEntity.ok(CommonResponse.success(savedProductService.save(actor, productId)));
    }

    // DELETE /me/saved-products/{productId}
    @Operation(summary = "저장 해제", description = "없는 것을 지워도 200 멱등.")
    @DeleteMapping("/me/saved-products/{productId}")
    public ResponseEntity<CommonResponse<SaveResultResponse>> remove(
            Actor actor,
            @Parameter(description = "제품 ID") @PathVariable Long productId) {

        return ResponseEntity.ok(CommonResponse.success(savedProductService.remove(actor, productId)));
    }
}
