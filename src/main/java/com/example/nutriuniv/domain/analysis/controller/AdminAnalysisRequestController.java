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

@Tag(name = "Admin - Analysis Request", description = "관리자 분석 대기 목록 (기능명세서 10.2)")
@RestController
@RequiredArgsConstructor
public class AdminAnalysisRequestController {

    private final AnalysisRequestService analysisRequestService;

    // GET /admin/analysis-requests
    @Operation(summary = "분석 대기 목록",
            description = "기본 정렬 = 요청 횟수 많은 순(수요 우선), 같으면 최근 요청순. type·status 로 필터. " +
                    "제품 등록·보강 자체는 기존 관리자 상품 API(엑셀 업로드·PATCH)를 씁니다.")
    @GetMapping("/admin/analysis-requests")
    public ResponseEntity<CommonResponse<AdminAnalysisRequestPageResponse>> list(
            @Parameter(description = "NEW_PRODUCT | NUTRITION_FILL") @RequestParam(required = false) String type,
            @Parameter(description = "WAITING | PROCESSING | DONE | HOLD") @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(analysisRequestService.list(type, status, page, size)));
    }

    // PATCH /admin/analysis-requests/{requestId}
    @Operation(summary = "대기 항목 상태 변경",
            description = "WAITING | PROCESSING | DONE | HOLD(단종·범위 밖). 완료해도 행을 지우지 않고 DONE 으로만 바꿉니다(수요 지표 보존). " +
                    "DONE·HOLD 로 바꾸면 처리 시각·처리자를 기록합니다.")
    @PatchMapping("/admin/analysis-requests/{requestId}")
    public ResponseEntity<CommonResponse<Void>> updateStatus(Actor actor,
                                                             @Parameter(description = "대기 항목 ID") @PathVariable Long requestId,
                                                             @RequestBody AdminAnalysisRequestStatusRequest request) {
        analysisRequestService.updateStatus(requestId, request.getStatus(), actor.userId());
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
