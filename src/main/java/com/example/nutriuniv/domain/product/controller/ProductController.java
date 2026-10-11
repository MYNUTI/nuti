package com.example.nutriuniv.domain.product.controller;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.UserPrincipal;
import com.example.nutriuniv.domain.product.dto.*;
import com.example.nutriuniv.domain.product.service.ProductExcelService;
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
    private final ProductExcelService productExcelService;
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
    @Operation(summary = "바코드 스캔 조회 (기능명세서 3.1·3.2)",
            description = "8·12·13·14자리를 13자리로 정규화해 조회합니다. 체크섬 불일치 400 BARCODE_CHECKSUM_INVALID(조회 안 함, 기록). " +
                    "데이터에 없으면 404 PRODUCT_NOT_FOUND + 대기 목록(NEW_PRODUCT) 자동 등록(같은 바코드 횟수 +1) — 인식 실패와 다른 화면. " +
                    "영양정보 부족이면 200 + status=INSUFFICIENT(grade·topReason 없음) + 대기 목록(NUTRITION_FILL) 등록. " +
                    "X-Anonymous-Id·토큰 선택 — 목표 미설정 시 일반 기준.")
    @GetMapping("/products/barcode/{barcode}")
    public ResponseEntity<CommonResponse<BarcodeScanResponse>> scanBarcode(
            Actor actor,
            @Parameter(description = "바코드 (숫자 8·12·13·14자리)") @PathVariable String barcode) {
        return ResponseEntity.ok(CommonResponse.success(productResultService.scan(barcode, actor)));
    }

    // GET /products/{productId}
    @Operation(summary = "제품 결과 화면 (기능명세서 2.3·5.1)",
            description = "status·product·nutrition·grade·topReason·appliedGoal·saved 블록으로 결과 화면을 한 번에 그립니다. 조회수 +1. " +
                    "영양정보 부족이면 status=INSUFFICIENT 로 제품명·이미지만(등급 없음) + 대기 목록(NUTRITION_FILL) 등록(404 아님). " +
                    "없는·비활성 제품 404 PRODUCT_NOT_FOUND. X-Anonymous-Id·토큰 선택 — saved·appliedGoal 이 소유자 기준으로 반영됩니다. " +
                    "1차 웹이 쓰던 필드(id·name·nutrients·coupang·pns·nutrientBounds 등)는 전환 기간 동안 함께 내려갑니다.")
    @GetMapping("/products/{productId}")
    public ResponseEntity<CommonResponse<ProductDetailResponse>> getProduct(
            Actor actor,
            @Parameter(description = "상품 ID") @PathVariable Long productId) {
        return ResponseEntity.ok(CommonResponse.success(productService.getProduct(productId, actor)));
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
    @Operation(summary = "상품 엑셀 업로드 등록",
            description = "xlsx 파일을 업로드하여 상품을 일괄 등록합니다. 중복 상품명은 덮어쓰기 처리됩니다.")
    @PostMapping(value = "/admin/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<ProductUploadResponse>> uploadProducts(
            @RequestPart("file") MultipartFile file) {

        return ResponseEntity.ok(CommonResponse.success(productExcelService.upload(file)));
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