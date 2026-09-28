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
    @Operation(summary = "저장 목록 (기능명세서 7.1)",
            description = "Bearer 또는 X-Anonymous-Id 필수(없으면 403 CONSENT_REQUIRED). 로그인·비로그인 같은 API, 소유자만 다릅니다. " +
                    "비활성 제품 제외, 등급 없는 제품은 grade=null. page·size는 1부터.")
    @GetMapping("/me/saved-products")
    public ResponseEntity<CommonResponse<SavedProductPageResponse>> getSaved(
            Actor actor,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(CommonResponse.success(savedProductService.getSaved(actor, page, size)));
    }

    // POST /me/saved-products/{productId}
    @Operation(summary = "제품 저장",
            description = "재저장은 200 멱등. 상한 200개 초과 409 SAVE_LIMIT_EXCEEDED, 없는 제품 404 PRODUCT_NOT_FOUND. " +
                    "동의 전(헤더 둘 다 없음) 403 CONSENT_REQUIRED → 클라이언트가 동의 화면 후 원래 저장을 이어갑니다.")
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
