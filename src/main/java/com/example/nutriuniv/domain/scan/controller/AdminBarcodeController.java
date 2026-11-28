package com.example.nutriuniv.domain.scan.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.scan.dto.BarcodeReviewDtos;
import com.example.nutriuniv.domain.scan.service.BarcodeAdminService;
import com.example.nutriuniv.domain.scan.service.BarcodePickService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin - Barcode", description = "바코드 확인 대기 · 이름을 아는 바코드 (v3 SCAN_008 · SCAN_009 · SCAN_010)")
@RestController
@RequiredArgsConstructor
public class AdminBarcodeController {

    private final BarcodeAdminService adminService;
    private final BarcodePickService pickService;

    // GET /admin/barcode-reviews
    @Operation(summary = "확인 대기 목록",
            description = "서로 다른 2명 이상이 같은 바코드에 같은 제품을 고른 줄. status 기본 PENDING (CONFIRMED · REJECTED).")
    @GetMapping("/admin/barcode-reviews")
    public ResponseEntity<CommonResponse<BarcodeReviewDtos.Page>> reviews(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(adminService.reviews(status, page, size)));
    }

    // POST /admin/barcode-reviews/{id}/decision
    @Operation(summary = "확인 대기 처리",
            description = "confirmed=true: 「이 제품인가요？」 선택은 최종 확정, 여러 개 고르기 선택은 그 바코드를 고른 제품에만 남깁니다. " +
                    "confirmed=false: 선택으로 이었던 연결을 끊고 그 선택을 지웁니다(다음 스캔에서 다시 묻습니다).")
    @PostMapping("/admin/barcode-reviews/{id}/decision")
    public ResponseEntity<CommonResponse<BarcodeReviewDtos.Item>> decide(
            Actor actor, @PathVariable long id, @RequestBody BarcodeReviewDtos.Decision decision) {
        var r = pickService.decide(id, decision != null && decision.confirmed(), actor.userId());
        return ResponseEntity.ok(CommonResponse.success(adminService.item(r)));
    }

    // POST /admin/barcode-names
    @Operation(summary = "이름을 아는 바코드 적재 (CSV)",
            description = "CSV(UTF-8) 열: barcode, product_name, company(선택). 우리 제품에 없는 바코드의 이름 · 회사 — " +
                    "스캔의 「비슷한 후보」(같은 회사 · 이름 비슷함 0.9 이상)와 「이름만 아는 바코드」 분기에 씁니다. 같은 바코드는 고칩니다.")
    @PostMapping(value = "/admin/barcode-names", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<BarcodeReviewDtos.NameUploadResponse>> uploadNames(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(CommonResponse.success(adminService.uploadNames(file)));
    }
}
