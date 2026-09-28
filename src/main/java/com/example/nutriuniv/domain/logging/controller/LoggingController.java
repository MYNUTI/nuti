package com.example.nutriuniv.domain.logging.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.logging.dto.CtaLogRequest;
import com.example.nutriuniv.domain.logging.dto.FilterLogRequest;
import com.example.nutriuniv.domain.logging.dto.ImpressionLogRequest;
import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.logging.dto.ScanEventLogRequest;
import com.example.nutriuniv.domain.logging.dto.SearchLogRequest;
import com.example.nutriuniv.domain.logging.dto.ViewLogRequest;
import com.example.nutriuniv.domain.logging.service.LoggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Logging", description = "로그 API")
@RestController
@RequiredArgsConstructor
public class LoggingController {

    // 클라이언트 식별(X-Anonymous-Id / X-Session-Id / X-Cohort)·IP 해석은 ActorArgumentResolver 가 담당

    private final LoggingService loggingService;

    // POST /logging/view
    @Operation(summary = "상품 조회 로그 기록 (product_detail_viewed)",
            description = "상품 상세 조회 시 클라이언트가 호출합니다. 유효 방문으로 집계됩니다. " +
                    "X-Anonymous-Id / X-Session-Id / X-Cohort 헤더로 비로그인 재방문을 추적합니다. " +
                    "로그 저장 실패 시에도 200을 반환합니다. 비로그인 시 user_id는 null로 저장됩니다.")
    @PostMapping("/logging/view")
    public ResponseEntity<CommonResponse<Void>> logView(Actor actor, @RequestBody ViewLogRequest request) {
        loggingService.logProductView(request, LogContext.from(actor));
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // POST /logging/cta
    @Operation(summary = "쿠팡 CTA 클릭 로그 기록 (coupang_outbound_clicked)",
            description = "쿠팡 링크 클릭 시 클라이언트가 호출합니다. " +
                    "로그인 유저는 동일 상품 1시간 내 중복 클릭을 무시합니다. " +
                    "로그 저장 실패 시에도 200을 반환합니다. 비로그인 시 user_id는 null로 저장됩니다.")
    @PostMapping("/logging/cta")
    public ResponseEntity<CommonResponse<Void>> logCta(Actor actor, @RequestBody CtaLogRequest request) {
        loggingService.logProductCta(request, LogContext.from(actor));
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // POST /logging/search
    @Operation(summary = "검색 로그 기록 (search_performed)",
            description = "검색 실행 시 클라이언트가 호출합니다. 유효 방문으로 집계됩니다. " +
                    "keyword가 빈 문자열이면 400을 반환합니다. " +
                    "로그 저장 실패 시에도 200을 반환합니다. 비로그인 시 user_id는 null로 저장됩니다.")
    @PostMapping("/logging/search")
    public ResponseEntity<CommonResponse<Void>> logSearch(Actor actor, @RequestBody SearchLogRequest request) {
        loggingService.logSearch(request, LogContext.from(actor));
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // POST /logging/filter
    @Operation(summary = "필터/정렬 로그 기록 (compare_or_filter_used)",
            description = "필터/정렬/등급정렬 사용 시 클라이언트가 호출합니다. 핵심 행동 지표입니다. " +
                    "filter_type이 빈 문자열이면 400을 반환합니다. " +
                    "로그 저장 실패 시에도 200을 반환합니다. 비로그인 시 user_id는 null로 저장됩니다.")
    @PostMapping("/logging/filter")
    public ResponseEntity<CommonResponse<Void>> logFilter(Actor actor, @RequestBody FilterLogRequest request) {
        loggingService.logFilter(request, LogContext.from(actor));
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // POST /logging/impression
    @Operation(summary = "상품 노출 로그 기록 (impression)",
            description = "목록/추천/검색 결과에 상품이 보여졌을 때 클라이언트가 호출합니다. " +
                    "한 화면에 보인 상품들을 items 배열로 배치 전송합니다(surface: LIST/RECOMMENDATION/SEARCH). " +
                    "추천 모델의 음성 샘플로 사용됩니다. 유효 방문에는 포함되지 않으며, " +
                    "세션 내 동일 상품 중복은 무시됩니다. 로그 저장 실패 시에도 200을 반환합니다.")
    @PostMapping("/logging/impression")
    public ResponseEntity<CommonResponse<Void>> logImpression(Actor actor, @RequestBody ImpressionLogRequest request) {
        loggingService.logImpressions(request, LogContext.from(actor));
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    // POST /logging/scan-event
    @Operation(summary = "스캔 실패 이벤트 기록 (기능명세서 9.1 신규)",
            description = "result: SCAN_FAIL(인식 실패) | NOT_IN_DATA(인식됐는데 데이터에 없음) | CHECKSUM_FAIL | PERMISSION_DENIED — " +
                    "원인별로 분리 적재합니다. barcode 는 NOT_IN_DATA·CHECKSUM_FAIL 일 때만 저장, surface 는 화면 식별자(선택). " +
                    "fire-and-forget: 저장 실패해도 200. 허용값 외 result 는 400. 유효 방문으로 집계되지 않습니다.")
    @PostMapping("/logging/scan-event")
    public ResponseEntity<CommonResponse<Void>> logScanEvent(Actor actor, @RequestBody ScanEventLogRequest request) {
        loggingService.logScanEvent(request, LogContext.from(actor));
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
