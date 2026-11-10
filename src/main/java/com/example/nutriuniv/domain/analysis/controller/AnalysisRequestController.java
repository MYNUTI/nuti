package com.example.nutriuniv.domain.analysis.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestCreateRequest;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestResponse;
import com.example.nutriuniv.domain.analysis.dto.PopularRequestResponse;
import com.example.nutriuniv.domain.analysis.service.AnalysisRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Contribution", description = "분석 요청 (v3 CTRB)")
@RestController
@RequiredArgsConstructor
public class AnalysisRequestController {

    private final AnalysisRequestService analysisRequestService;

    // POST /analysis-requests
    @Operation(summary = "분석 요청 보내기 (v3 CTRB)",
            description = """
                    제품명 요청: productName(100자) 필수 · brandName(50자) 선택 · originBarcode / originKeyword(어디서 왔는지) 선택 — 늘 새 요청.
                    NEW_PRODUCT: barcode(8·12·13·14자리 → 13자리) 또는 keyword(30자). NUTRITION_FILL: productId.
                    바코드 · 검색어 · 제품 요청은 같은 대상이면 한 요청으로 모으고, 요청한 기기 수(requestCount)는 기기당 한 번만 셉니다.
                    alreadyRequested=true 면 먼저 요청한 기기가 있었거나 이 기기가 이미 요청한 것 — message 를 그대로 보여 주면 됩니다.
                    분석 완료 제품 409 ALREADY_ANALYZED. 검색어 · 제품명 요청은 합쳐 하루 5건 초과 429.
                    """)
    @PostMapping("/analysis-requests")
    public ResponseEntity<CommonResponse<AnalysisRequestResponse>> submit(Actor actor,
                                                                          @RequestBody AnalysisRequestCreateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(analysisRequestService.submit(request, actor)));
    }

    // GET /analysis-requests/popular
    @Operation(summary = "많이 요청된 제품 (v3 CTRB_006)",
            description = "아직 분석 전(분석 중 · 확인 중)인 요청을 요청한 기기 수 많은 순으로. 바코드만 있는 요청은 이름이 없어 빠집니다.")
    @GetMapping("/analysis-requests/popular")
    public ResponseEntity<CommonResponse<PopularRequestResponse>> popular(
            @Parameter(description = "개수 (1~30)") @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(CommonResponse.success(analysisRequestService.popular(limit)));
    }
}
