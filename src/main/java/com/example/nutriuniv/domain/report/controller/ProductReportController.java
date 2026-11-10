package com.example.nutriuniv.domain.report.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.report.dto.ReportCreateRequest;
import com.example.nutriuniv.domain.report.dto.ReportCreateResponse;
import com.example.nutriuniv.domain.report.service.ProductReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "제품 오류 제보 (기능명세서 10.4, 2차)")
@RestController
@RequiredArgsConstructor
public class ProductReportController {

    private final ProductReportService productReportService;

    // POST /products/{productId}/reports
    @Operation(summary = "오류 제보 (v3 CTRB_008)",
            description = "reportTypes 여러 개 — WRONG_PRODUCT(다른 제품) | NAME_OR_IMAGE(이름 · 사진) | NUTRITION(영양정보) | OTHER(기타). 고른 항목마다 한 제보가 됩니다. " +
                    "옛 reportType 하나 · NAME · IMAGE · 한글 이름도 받습니다. content 1000자 — OTHER 를 고르면 필수. barcode(본 바코드) 선택. 연락처는 받지 않습니다. " +
                    "같은 사람 · 같은 제품 · 같은 항목이 아직 확인 중이면 새로 만들지 않고 duplicateTypes 로 알려 줍니다(전부 겹치면 duplicate=true). " +
                    "비로그인 가능. 같은 사용자 하루 10건 초과 429. 없는 · 비활성 제품 404.")
    @PostMapping("/products/{productId}/reports")
    public ResponseEntity<CommonResponse<ReportCreateResponse>> submit(Actor actor,
                                                                       @Parameter(description = "제품 ID") @PathVariable Long productId,
                                                                       @RequestBody ReportCreateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(productReportService.submit(productId, request, actor)));
    }
}
