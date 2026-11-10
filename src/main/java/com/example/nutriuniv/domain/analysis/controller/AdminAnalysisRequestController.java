package com.example.nutriuniv.domain.analysis.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.analysis.dto.AdminAnalysisRequestPageResponse;
import com.example.nutriuniv.domain.analysis.dto.AdminAnalysisRequestStatusRequest;
import com.example.nutriuniv.domain.analysis.service.AnalysisRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Analysis Request", description = "관리자 분석 요청 대기열 (v3 ADM_002)")
@RestController
@RequiredArgsConstructor
public class AdminAnalysisRequestController {

    private final AnalysisRequestService analysisRequestService;

    // GET /admin/analysis-requests
    @Operation(summary = "분석 요청 대기열",
            description = "기본 정렬 = 요청한 기기 수 많은 순, 같으면 최근 요청순. type · kind(BARCODE | KEYWORD | NAME | PRODUCT) · status 로 거릅니다. " +
                    "statusCounts 는 상태별 전체 건수. 제품명 요청은 productName · brandName · originBarcode · originKeyword 를 함께 줍니다. " +
                    "제품 등록 · 보강 자체는 기존 관리자 상품 API(엑셀 업로드 · PATCH)를 씁니다.")
    @GetMapping("/admin/analysis-requests")
    public ResponseEntity<CommonResponse<AdminAnalysisRequestPageResponse>> list(
            @Parameter(description = "NEW_PRODUCT | NUTRITION_FILL") @RequestParam(required = false) String type,
            @Parameter(description = "BARCODE | KEYWORD | NAME | PRODUCT") @RequestParam(required = false) String kind,
            @Parameter(description = "ANALYZING | REVIEWING | DONE | FAILED") @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(analysisRequestService.list(type, kind, status, page, size)));
    }

    // PATCH /admin/analysis-requests/{requestId}
    @Operation(summary = "요청 상태 변경",
            description = "ANALYZING(분석 중) | REVIEWING(확인 중) | DONE(완료) | FAILED(분석 못 함). 옛 값 WAITING · PROCESSING · HOLD 도 새 값으로 받습니다. " +
                    "완료해도 행을 지우지 않습니다(수요 지표). DONE · FAILED 로 바꾸면 처리 시각 · 처리자를 기록합니다.")
    @PatchMapping("/admin/analysis-requests/{requestId}")
    public ResponseEntity<CommonResponse<Void>> updateStatus(Actor actor,
                                                             @Parameter(description = "요청 ID") @PathVariable Long requestId,
                                                             @RequestBody AdminAnalysisRequestStatusRequest request) {
        analysisRequestService.updateStatus(requestId, request.getStatus(), actor.userId());
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
