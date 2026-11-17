package com.example.nutriuniv.domain.publicdata.controller;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import com.example.nutriuniv.domain.publicdata.dto.PublicApiRoundResponse;
import com.example.nutriuniv.domain.publicdata.dto.PublicApiStatusResponse;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;
import com.example.nutriuniv.domain.publicdata.service.PublicApiCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@Tag(name = "Admin · Public API", description = "공공 API 수집 (원본 표에 그대로 쌓기)")
@RestController
@RequestMapping("/admin/public-api")
@RequiredArgsConstructor
public class AdminPublicApiController {

    private final PublicApiCollector collector;

    @Operation(summary = "수집 상태", description = "API 13개마다 최근 회차 · 마지막 완료 시각 · 원본 행 수 · 오늘 호출 수 · 하루 한도.")
    @GetMapping("/status")
    public ResponseEntity<CommonResponse<List<PublicApiStatusResponse>>> status() {
        return ResponseEntity.ok(CommonResponse.success(collector.status()));
    }

    @Operation(summary = "회차 열기",
            description = "api: NUTRI_DB · FOODQR_LIST · FOODQR_NUTRITION · FOODQR_MAKER · HACCP · C005 · I2570 · C002 · C006 · I2852 · I0490 · I0300 · I1420. " +
                    "mode: FULL(전량) · INCREMENTAL(증분, CHNG_DT 를 쓰는 C005 · I2570 · C002 · C006 · I2852 만). " +
                    "condition: 증분이면 yyyyMMdd(오늘부터 7일 전까지), I0300 · I1420 전량이면 연도 yyyy. " +
                    "이미 받는 중인 회차가 있으면 409. 여는 것만 하고 받지는 않는다 — 받기는 step.")
    @PostMapping("/{api}/rounds")
    public ResponseEntity<CommonResponse<PublicApiRoundResponse>> start(
            @Parameter(description = "API 이름") @PathVariable String api,
            @Parameter(description = "FULL · INCREMENTAL") @RequestParam(defaultValue = "FULL") String mode,
            @Parameter(description = "증분 yyyyMMdd · 연도 yyyy") @RequestParam(required = false) String condition) {
        return ResponseEntity.ok(CommonResponse.success(collector.start(parseApi(api), parseMode(mode), condition)));
    }

    @Operation(summary = "회차 이어 받기",
            description = "호출 한도 안에서 최대 maxCalls 페이지를 받는다(하루 한도 90% · 식품안전나라 낮 시간 270회 · CHNG_DT 없이 1시간 90회). " +
                    "한도 초과 · 서버 오류면 멈추고 회차는 RUNNING 으로 남는다(다시 부르면 이어 받음). " +
                    "끝까지 받으면 DONE, 행 수가 맞지 않거나 키 거부 · 오류면 FAILED(받은 행은 지움).")
    @PostMapping("/rounds/{roundId}/step")
    public ResponseEntity<CommonResponse<PublicApiRoundResponse>> step(
            @PathVariable long roundId,
            @Parameter(description = "이번에 부를 최대 페이지 수 (1~1000)") @RequestParam(defaultValue = "5") int maxCalls) {
        if (maxCalls < 1 || maxCalls > 1000) throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "maxCalls 는 1~1000 입니다.");
        return ResponseEntity.ok(CommonResponse.success(collector.step(roundId, maxCalls)));
    }

    @Operation(summary = "회차 취소", description = "받는 중인 회차를 실패로 돌리고 받은 행을 지운다.")
    @PostMapping("/rounds/{roundId}/cancel")
    public ResponseEntity<CommonResponse<PublicApiRoundResponse>> cancel(@PathVariable long roundId) {
        return ResponseEntity.ok(CommonResponse.success(collector.cancel(roundId)));
    }

    private static PublicApi parseApi(String raw) {
        try {
            return PublicApi.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "없는 API 입니다: " + raw);
        }
    }

    private static PublicApiRound.Mode parseMode(String raw) {
        try {
            return PublicApiRound.Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "mode 는 FULL · INCREMENTAL 입니다: " + raw);
        }
    }
}