package com.example.nutriuniv.domain.history.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.history.dto.HistoryResponse;
import com.example.nutriuniv.domain.history.service.ViewHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "History", description = "최근 본 제품 (v3 RTN_001)")
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final ViewHistoryService viewHistoryService;

    @Operation(summary = "최근 본 제품 (v3 RTN_001)",
            description = "Bearer 또는 X-Anonymous-Id 필수(없으면 400 ANONYMOUS_ID_REQUIRED). 결과 화면(GET /products/{id}, 바코드 스캔 RESULT·NO_SCORE)을 열 때 서버가 기록합니다. " +
                    "제품별로 묶어 마지막으로 본 순, 비활성 제품 제외(단종·생산 중단은 상태 배지로 남김). " +
                    "grade(여러 개 OR, A~E·NONE)는 요청 목표 기준. product.better: TOP(A·B) / AVAILABLE(같은 중분류에 더 높은 점수 있음) / NONE. page는 0부터.")
    @GetMapping("/me/history")
    public ResponseEntity<CommonResponse<HistoryResponse>> list(
            Actor actor,
            @Parameter(description = "WEIGHT_LOSS(기본) | MUSCLE_GAIN") @RequestParam(required = false) String goal,
            @Parameter(description = "하루 칼로리 구간 (1500·2000·2500·3000)") @RequestParam(required = false) Integer kcalBand,
            @Parameter(description = "등급 필터 A~E · NONE") @RequestParam(required = false) List<String> grade,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(CommonResponse.success(viewHistoryService.list(actor, goal, kcalBand, grade, page, size)));
    }
}
