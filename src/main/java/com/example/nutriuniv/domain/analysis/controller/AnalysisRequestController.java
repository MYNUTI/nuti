package com.example.nutriuniv.domain.analysis.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestCreateRequest;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestResponse;
import com.example.nutriuniv.domain.analysis.service.AnalysisRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Contribution", description = "분석 대기 등록 (기능명세서 10.2)")
@RestController
@RequiredArgsConstructor
public class AnalysisRequestController {

    private final AnalysisRequestService analysisRequestService;

    // POST /analysis-requests
    @Operation(summary = "분석 대기 등록 (명세 10.2 사용자 접수)",
            description = """
                    유형 2종을 섞지 않습니다. NEW_PRODUCT = barcode(8·12·13·14자리, 13자리로 정규화) 또는 keyword(30자 이내),
                    NUTRITION_FILL = productId. 유형별 식별값 누락 400. 같은 식별값 재접수는 requestCount 만 +1(새 행 없음).
                    분석 완료 제품 409 ALREADY_ANALYZED. keyword 접수(검색 0건 폴백)는 같은 사용자 하루 5건 초과 429.
                    X-Anonymous-Id 는 선택 — 한도 계산과 마이 화면 기여 집계에 쓰입니다. 스캔·결과 조회의 자동 등록과 같은 표에 쌓입니다.
                    """)
    @PostMapping("/analysis-requests")
    public ResponseEntity<CommonResponse<AnalysisRequestResponse>> submit(Actor actor,
                                                                          @RequestBody AnalysisRequestCreateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(analysisRequestService.submit(request, actor)));
    }
}
