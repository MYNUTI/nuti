package com.example.nutriuniv.domain.product.controller;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.UserPrincipal;
import com.example.nutriuniv.domain.product.dto.*;
import com.example.nutriuniv.domain.product.service.ProductCsvService;
import com.example.nutriuniv.domain.product.service.ProductResultService;
import com.example.nutriuniv.domain.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Product", description = "상품 API")
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCsvService productCsvService;
    private final ProductResultService productResultService;

    // GET /products/nutrient-claims
    @Operation(summary = "영양 강조표시 필터 목록 조회",
            description = "상품 필터링에 사용 가능한 영양 강조표시 코드와 라벨 목록을 반환합니다. " +
                    "GET /products의 nutrientClaims 파라미터에 code 값을 사용하세요.")
    @GetMapping("/products/nutrient-claims")
    public ResponseEntity<CommonResponse<List<NutrientClaimResponse>>> getNutrientClaims() {
        return ResponseEntity.ok(CommonResponse.success(NutrientClaimResponse.getAllClaims()));
    }

    // GET /products
    @Operation(summary = "상품 목록 조회",
            description = "키워드, 카테고리, 브랜드, 영양소 범위 필터로 상품 목록을 조회합니다. " +
                    "nutrientClaims 파라미터로 영양 강조표시 필터를 적용할 수 있습니다 (복수 선택 가능, AND 조건). " +
                    "사용 가능한 값: GET /products/nutrient-claims 참조. " +
                    "로그인 시 찜 여부(isFavorited)가 반영됩니다.")
    @GetMapping("/products")
    public ResponseEntity<CommonResponse<ProductPageResponse>> getProducts(
            @AuthenticationPrincipal UserPrincipal principal,
            @ModelAttribute ProductSearchRequest request) {

        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(CommonResponse.success(productService.getProducts(request, userId)));
    }

    // GET /products/barcode/{barcode}
    @Operation(summary = "바코드 스캔 조회 (v3 SCAN_010)",
            description = "8·12·13·14자리를 정규화해 조회합니다. 체크섬 불일치 400 BARCODE_CHECKSUM_INVALID(조회 안 함, 기록). " +
                    "outcome 으로 갈래를 나눕니다 — RESULT: 분석 완료 제품 하나(단종·생산 중단 포함, result 에 결과 화면 전체). " +
                    "MULTIPLE: 바코드에 제품이 둘 이상(candidates 에서 골라 GET /products/{id}). " +
                    "NO_SCORE: 성분 7가지 중 빠진 것이 있음(result.goals 빈 배열) + 성분값 보강 요청 자동 등록. " +
                    "NOT_FOUND: 어디에도 없음 + 새 제품 요청 자동 등록(같은 바코드 횟수 +1). " +
                    "kcalBand(1500·2000·2500·3000, 없거나 그 밖이면 2000)는 감량 점수에만 씁니다.")
    @GetMapping("/products/barcode/{barcode}")
    public ResponseEntity<CommonResponse<ScanResultResponse>> scanBarcode(
            Actor actor,
            @Parameter(description = "바코드 (숫자 8·12·13·14자리)") @PathVariable String barcode,
            @Parameter(description = "하루 칼로리 구간 (1500·2000·2500·3000)") @RequestParam(required = false) Integer kcalBand) {
        return ResponseEntity.ok(CommonResponse.success(productResultService.scan(barcode, kcalBand, actor)));
    }

    // GET /products/{productId}
    @Operation(summary = "제품 결과 화면 (v3 RSLT_002)",
            description = "두 목표(감량·근육 증가)의 점수·등급·한 줄 해석·좋은 점·아쉬운 점, 성분별 100g·1회 제공량 값과 1일 기준치 %·단계, " +
                    "제품 상태(점수 없음·분석 완료·생산 중단·재고 판매 중·단종), 쿠팡 구매 줄, 저장 여부를 한 번에 줍니다. 조회수 +1. " +
                    "점수가 없으면 goals 빈 배열 + 성분값 보강 요청 자동 등록(404 아님). 없는·비활성 제품 404 PRODUCT_NOT_FOUND. " +
                    "kcalBand(1500·2000·2500·3000, 없거나 그 밖이면 2000)는 감량 점수에만 씁니다. 기준선 수치는 내려 주지 않습니다.")
    @GetMapping("/products/{productId}")
    public ResponseEntity<CommonResponse<ProductResultResponse>> getProduct(
            Actor actor,
            @Parameter(description = "상품 ID") @PathVariable Long productId,
            @Parameter(description = "하루 칼로리 구간 (1500·2000·2500·3000)") @RequestParam(required = false) Integer kcalBand) {
        return ResponseEntity.ok(CommonResponse.success(productService.getProduct(productId, kcalBand, actor)));
    }

    // GET /admin/products
    @Operation(summary = "관리자 상품 목록 조회",
            description = "활성여부, 쿠팡 연동상태 등 관리자 전용 필터를 포함합니다. " +
                    "is_active 미입력 시 전체(활성+비활성) 반환.")
    @GetMapping("/admin/products")
    public ResponseEntity<CommonResponse<AdminProductPageResponse>> getAdminProducts(
            @ModelAttribute AdminProductSearchRequest request) {

        return ResponseEntity.ok(CommonResponse.success(productService.getAdminProducts(request)));
    }

    // POST /admin/products
    @Operation(summary = "제품 적재 (데이터 팀 CSV)",
            description = "데이터 팀 최종 테이블 CSV(UTF-8)를 올려 master_id 기준으로 새로 만들거나 갱신합니다. 이름으로 합치지 않습니다. " +
                    "바코드는 13자리로 맞춰 저장(N:M), category 는 중분류 코드(비면 분류 제외), content_hash 가 같으면 건너뜀, " +
                    "운영자가 고친 제품은 건너뜀. 제품별로 따로 저장해 실패한 제품만 줄 번호·이유와 함께 돌려줍니다. 적재 중에는 다른 적재가 409.")
    @PostMapping(value = "/admin/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<ProductUploadResponse>> uploadProducts(
            @RequestPart("file") MultipartFile file) {

        return ResponseEntity.ok(CommonResponse.success(productCsvService.upload(file)));
    }

    // DELETE /admin/products/reset?confirm=RESET_ALL_DATA
    // products TRUNCATE CASCADE → 참조 테이블(pns·벡터·로그) 전부 소실. 실수 호출(Swagger 원클릭 등) 방지용 확인 문구 필수.
    private static final String RESET_CONFIRM_PHRASE = "RESET_ALL_DATA";

    @Operation(summary = "전체 초기화",
            description = "상품, 영양정보, 브랜드, 카테고리, 리뷰, 찜을 모두 삭제하고 시퀀스(id)를 1로 리셋합니다. " +
                    "복구 불가. confirm 파라미터에 'RESET_ALL_DATA'를 정확히 넣어야 실행됩니다.")
    @DeleteMapping("/admin/products/reset")
    public ResponseEntity<CommonResponse<Void>> resetAll(
            @Parameter(description = "확인 문구 (RESET_ALL_DATA)") @RequestParam(required = false) String confirm) {

        if (!RESET_CONFIRM_PHRASE.equals(confirm)) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "전체 초기화는 confirm=" + RESET_CONFIRM_PHRASE + " 파라미터가 필요합니다.");
        }
        productService.resetAll();
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // PATCH /admin/products/{productId}
    @Operation(summary = "상품 수정",
            description = "전달된 필드만 수정합니다. null 필드는 기존 값을 유지합니다. " +
                    "이름·이미지·분류·브랜드·바코드를 고치면 수정 이력(product_change_logs)을 남기고 재적재 보호(manuallyCorrected)를 켭니다 — 명세 10.4. " +
                    "manuallyCorrected=false 로 보호를 해제할 수 있습니다.")
    @PatchMapping("/admin/products/{productId}")
    public ResponseEntity<CommonResponse<Void>> updateProduct(
            Actor actor,
            @Parameter(description = "상품 ID") @PathVariable Long productId,
            @RequestBody AdminProductUpdateRequest request) {

        productService.updateProduct(productId, request, actor.userId());
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // DELETE /admin/products/{productId}
    @Operation(summary = "상품 비활성화",
            description = "특정 상품을 소프트 딜리트(is_active=false) 처리합니다. 이미 비활성화된 상품은 409 반환.")
    @DeleteMapping("/admin/products/{productId}")
    public ResponseEntity<CommonResponse<Void>> deactivateProduct(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {

        productService.deactivateProduct(productId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}