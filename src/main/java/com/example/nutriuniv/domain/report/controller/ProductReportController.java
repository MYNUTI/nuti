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
    @Operation(summary = "오류 제보 (명세 10.4)",
            description = "reportType NUTRITION(영양정보) | NAME(제품명) | IMAGE(이미지) | OTHER(기타) — 한글 이름도 받습니다. content 필수(1000자), contactEmail 선택(형식 검사). " +
                    "비로그인 가능. 같은 사용자 하루 10건 초과 429 (X-Anonymous-Id 없으면 세션 기준). 없는·비활성 제품 404.")
    @PostMapping("/products/{productId}/reports")
    public ResponseEntity<CommonResponse<ReportCreateResponse>> submit(Actor actor,
                                                                       @Parameter(description = "제품 ID") @PathVariable Long productId,
                                                                       @RequestBody ReportCreateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(productReportService.submit(productId, request, actor)));
    }
}
