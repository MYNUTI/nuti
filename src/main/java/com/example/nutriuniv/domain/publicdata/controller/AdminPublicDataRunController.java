package com.example.nutriuniv.domain.publicdata.controller;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.publicdata.dto.NoCategoryPageResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncRunResponse;
import com.example.nutriuniv.domain.publicdata.dto.TickResponse;
import com.example.nutriuniv.domain.publicdata.repository.SyncReviewRepository;
import com.example.nutriuniv.domain.publicdata.service.PublicDataRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin · Public Data Run", description = "공공데이터 최신화 — 스케줄러가 하는 일을 손으로 돌리기 · 검토 목록")
@RestController
@RequiredArgsConstructor
public class AdminPublicDataRunController {

    private final PublicDataRunner runner;
    private final SyncReviewRepository reviewRepository;

    @Operation(summary = "수집 한 번 돌리기",
            description = "스케줄러가 10분마다 하는 일 — 주기가 된 API 의 회차를 열고, 받는 중인 회차를 한도 안에서 이어 받는다. " +
                    "식품안전나라 큰 전량은 밤(19~06시)에만 받는다.")
    @PostMapping("/admin/public-api/tick")
    public ResponseEntity<CommonResponse<TickResponse>> tick() {
        return ResponseEntity.ok(CommonResponse.success(runner.tick()));
    }

    @Operation(summary = "재계산 → 반영 한 번 돌리기",
            description = "스케줄러가 매일 05:40 에 하는 일. 막는 이유(API 미완료 등)가 있으면 돌지 않고 이유를 돌려준다. " +
                    "force=false 면 마지막 재계산 뒤 새로 끝난 회차가 있을 때만 돈다. " +
                    "allowMassDeactivation=true 면 사라진 제품이 출처 적재 제품의 10%를 넘어도 비활성으로 돌린다.")
    @PostMapping("/admin/product-sync/run")
    public ResponseEntity<CommonResponse<SyncRunResponse>> run(
            @RequestParam(defaultValue = "true") boolean force,
            @RequestParam(defaultValue = "false") boolean allowMassDeactivation) {
        return ResponseEntity.ok(CommonResponse.success(runner.syncRun(force, allowMassDeactivation)));
    }

    @Operation(summary = "검토 목록 — 분류 없는 제품",
            description = "공공데이터로 들어온 활성 제품 중 분류가 비어 있는 것, 최신순. 분류는 규칙으로 만들 수 없어 새 제품은 비어 있다.")
    @GetMapping("/admin/product-sync/review/no-category")
    public ResponseEntity<CommonResponse<NoCategoryPageResponse>> noCategory(
            @Parameter(description = "0부터") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "1~200") @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 200) throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page 는 0 이상, size 는 1~200 입니다.");
        return ResponseEntity.ok(CommonResponse.success(reviewRepository.findNoCategory(page, size)));
    }
}