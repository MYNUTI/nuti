package com.example.nutriuniv.domain.admin.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangBulkSyncResponse;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangLinkPageResponse;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangSyncResponse;
import com.example.nutriuniv.domain.admin.service.AdminCoupangService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Coupang", description = "관리자 쿠팡 연동 API")
@RestController
@RequiredArgsConstructor
public class AdminCoupangController {

    private final AdminCoupangService adminCoupangService;

    // GET /admin/coupang/links
    @Operation(summary = "쿠팡 연동 목록 조회",
            description = "status 미입력 시 전체 조회. 허용 값: LINKED / UNLINKED / FAILED / REVIEW. " +
                    "REVIEW 는 후보는 찾았는데 한쪽 상품명에 용량(g · mL)이나 개수가 없어 자동으로 잇지 않은 것 — " +
                    "POST /admin/coupang/links/{productId}/review 로 확인합니다. matchType 은 EXACT(같은 용량) · OTHER_SIZE(다른 용량) · NAME_SEARCH(용량 확인 안 됨).")
    @GetMapping("/admin/coupang/links")
    public ResponseEntity<CommonResponse<AdminCoupangLinkPageResponse>> getCoupangLinks(
            @Parameter(description = "연동 상태 필터 (LINKED / UNLINKED / FAILED / REVIEW)")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(CommonResponse.success(
                adminCoupangService.getCoupangLinks(status, page, size)));
    }

    // POST /admin/coupang/sync
    @Operation(summary = "쿠팡 링크 일괄 매핑",
            description = "UNLINKED 상태인 전체 상품을 쿠팡 파트너스 API로 연결합니다(호출마다 3초). " +
                    "이름 유사도 60점 이상 후보 가운데 상품명의 1개 용량 · 개수가 같은 상품 → 다른 용량 상품 순으로 잇고, " +
                    "한쪽이라도 용량이나 개수가 없으면 REVIEW 로 둡니다. 못 찾으면 키워드를 두 번까지 줄여 다시 찾습니다.")
    @PostMapping("/admin/coupang/sync")
    public ResponseEntity<CommonResponse<AdminCoupangBulkSyncResponse>> bulkSyncCoupangLinks() {
        return ResponseEntity.ok(CommonResponse.success(
                adminCoupangService.bulkSyncCoupangLinks()));
    }

    // POST /admin/coupang/retry
    @Operation(summary = "쿠팡 링크 일괄 재시도",
            description = "FAILED 상태인 전체 상품을 같은 규칙으로 재시도합니다(호출마다 3초).")
    @PostMapping("/admin/coupang/retry")
    public ResponseEntity<CommonResponse<AdminCoupangBulkSyncResponse>> retryCoupangLinks() {
        return ResponseEntity.ok(CommonResponse.success(
                adminCoupangService.retryCoupangLinks()));
    }

    @Operation(summary = "쿠팡 연결 확인 (REVIEW)",
            description = "확인 대기 후보를 정합니다. SAME = 같은 용량으로 연결(판매가 · 100g당 · 1개당 노출), " +
                    "OTHER = 다른 용량으로 연결(묶음 수 · 1개당만), REJECT = 다른 제품 — 찾지 못함(FAILED). REVIEW 가 아니면 409.")
    @PostMapping("/admin/coupang/links/{productId}/review")
    public ResponseEntity<CommonResponse<AdminCoupangSyncResponse>> reviewCoupangLink(
            @Parameter(description = "상품 ID") @PathVariable Long productId,
            @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                adminCoupangService.review(productId, request == null ? null : request.decision())));
    }

    public record ReviewRequest(AdminCoupangService.ReviewDecision decision) {}

    @Operation(summary = "쿠팡 판매가 새로 받기 (전체)",
            description = "LINKED 상품 전부를 그 상품을 찾은 검색어로 다시 검색해 판매가 · 조회한 날을 새로 받습니다(호출마다 3초). " +
                    "쿠팡 응답에 품절 칸이 없어, 검색은 됐는데 연결한 상품이 2번 이어서 안 나오면 판매 상태 확인 못 함 — " +
                    "가격을 숨기고 버튼은 쿠팡 검색 결과로 갑니다. API 오류는 세지 않고 직전 값을 그대로 둡니다. " +
                    "successCount = 다시 검색됨, failCount = 안 나옴 + API 오류.")
    @PostMapping("/admin/coupang/refresh")
    public ResponseEntity<CommonResponse<AdminCoupangBulkSyncResponse>> refreshCoupangLinks() {
        return ResponseEntity.ok(CommonResponse.success(adminCoupangService.refreshAll()));
    }

    @Operation(summary = "쿠팡 판매가 새로 받기 (단건)", description = "LINKED 가 아니면 409.")
    @PostMapping("/admin/coupang/refresh/{productId}")
    public ResponseEntity<CommonResponse<AdminCoupangSyncResponse>> refreshCoupangLink(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {
        return ResponseEntity.ok(CommonResponse.success(adminCoupangService.refresh(productId)));
    }

    // POST /admin/coupang/sync/{productId}
    @Operation(summary = "쿠팡 링크 단건 매핑",
            description = "특정 상품의 쿠팡 파트너스 링크를 매핑합니다. " +
                    "API 실패 시 link_status=FAILED로 저장됩니다.")
    @PostMapping("/admin/coupang/sync/{productId}")
    public ResponseEntity<CommonResponse<AdminCoupangSyncResponse>> syncCoupangLink(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {

        return ResponseEntity.ok(CommonResponse.success(
                adminCoupangService.syncCoupangLink(productId)));
    }
}
