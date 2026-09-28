package com.example.nutriuniv.domain.admin.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.admin.dto.DataStatusResponse;
import com.example.nutriuniv.domain.admin.dto.RetentionMetricsResponse;
import com.example.nutriuniv.domain.admin.service.AdminMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Metrics", description = "관리자 지표 — 재방문(9.2)·데이터 현황(10.5)")
@RestController
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    // GET /admin/metrics/retention
    @Operation(summary = "재방문 집계 (명세 9.2)",
            description = "모수 = 0~6일차 유효 방문한 「동의한 사용자」(익명 ID 발급자) 중 첫 방문이 from~to(yyyyMMdd, KST, 둘 다 선택) 인 사람. " +
                    "재방문 = 그중 7~27일차 유효 방문 1회 이상. verdict PASS(20%↑)·HOLD(7~20%)·FAIL(7%↓). definition 에 세션 30분·유효방문·창·모수 변경 시점. " +
                    "기존 /admin/stats/retention(cohort별, 1차 정의)은 그대로 둡니다.")
    @GetMapping("/admin/metrics/retention")
    public ResponseEntity<CommonResponse<RetentionMetricsResponse>> retention(
            @Parameter(description = "시작일 yyyyMMdd (선택)") @RequestParam(required = false) String from,
            @Parameter(description = "종료일 yyyyMMdd, 포함 (선택)") @RequestParam(required = false) String to) {
        return ResponseEntity.ok(CommonResponse.success(adminMetricsService.retention(from, to)));
    }

    // GET /admin/dashboard/data-status
    @Operation(summary = "데이터 현황 대시보드 (명세 10.5)",
            description = "전체 제품 수 · 분석 완료 비율 · 바코드 보유 비율 · 등급 분포(일반 기준, 재산출 필요 신호) · " +
                    "대분류별 분석 완료 비율(랭킹 게이트 300건+A·D 통과 여부와 함께) · 마지막 적재일 · 분석 대기 건수.")
    @GetMapping("/admin/dashboard/data-status")
    public ResponseEntity<CommonResponse<DataStatusResponse>> dataStatus() {
        return ResponseEntity.ok(CommonResponse.success(adminMetricsService.dataStatus()));
    }
}
