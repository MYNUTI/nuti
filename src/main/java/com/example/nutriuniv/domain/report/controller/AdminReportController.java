package com.example.nutriuniv.domain.report.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.report.dto.AdminReportPageResponse;
import com.example.nutriuniv.domain.report.dto.AdminReportUpdateRequest;
import com.example.nutriuniv.domain.report.service.ProductReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Report", description = "제보 목록·처리 (기능명세서 10.4)")
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final ProductReportService productReportService;

    // GET /admin/reports
    @Operation(summary = "제보 목록",
            description = "최근 접수순. status 로 필터(RECEIVED | PROCESSING | DONE | REJECTED). 실제 데이터 수정은 PATCH /admin/products/{id}(이름·이미지·분류·브랜드·바코드) 와 " +
                    "PATCH /admin/products/{id}/nutrients(영양성분 → 등급 재계산) 로 하고, 처리 결과를 여기서 DONE 으로 남깁니다.")
    @GetMapping("/admin/reports")
    public ResponseEntity<CommonResponse<AdminReportPageResponse>> list(
            @Parameter(description = "RECEIVED | PROCESSING | DONE | REJECTED") @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(productReportService.list(status, page, size)));
    }

    // PATCH /admin/reports/{reportId}
    @Operation(summary = "제보 처리",
            description = "status·memo 둘 다 선택(둘 다 없으면 400). DONE 으로 바꾸면(영양정보·제품명·이미지 유형) 그 제품에 재적재 보호를 켜고 수정 이력을 남깁니다. " +
                    "REJECTED 는 수정 없음. DONE·REJECTED 에 처리자·시각 기록.")
    @PatchMapping("/admin/reports/{reportId}")
    public ResponseEntity<CommonResponse<Void>> update(Actor actor,
                                                       @Parameter(description = "제보 ID") @PathVariable Long reportId,
                                                       @RequestBody AdminReportUpdateRequest request) {
        productReportService.update(reportId, request, actor.userId());
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
