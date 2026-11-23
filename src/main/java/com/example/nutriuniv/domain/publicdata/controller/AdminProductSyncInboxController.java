package com.example.nutriuniv.domain.publicdata.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.publicdata.dto.InboxListResponse;
import com.example.nutriuniv.domain.publicdata.dto.InboxRunResponse;
import com.example.nutriuniv.domain.publicdata.service.ProductSyncInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin · Product Sync Inbox", description = "파이프라인이 /data/csv 에 둔 34컬럼 CSV 를 제품 데이터에 반영")
@RestController
@RequestMapping("/admin/product-sync/inbox")
@RequiredArgsConstructor
public class AdminProductSyncInboxController {

    private final ProductSyncInboxService inboxService;

    @Operation(summary = "수신함 CSV 바로 반영",
            description = "자동 실행(매일 08:00)을 기다리지 않고 반영합니다. fileName 을 비우면 가장 새 product_master_YYYYMMDD.csv 를 처리합니다. " +
                    "처리는 뒤에서 돌고 202 로 바로 돌아옵니다 — 결과는 GET /admin/product-sync/inbox 로 봅니다. " +
                    "이미 반영한 파일은 409(force=true 면 다시 처리). 다른 적재·반영이나 다른 CSV 처리 중이면 409.")
    @PostMapping("/run")
    public ResponseEntity<CommonResponse<InboxRunResponse>> run(
            @Parameter(description = "처리할 파일 이름 (예: product_master_20261123.csv). 비우면 가장 새 파일")
            @RequestParam(required = false) String fileName,
            @Parameter(description = "이미 반영한 파일도 다시 처리할지 (기본 false)")
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonResponse.success(inboxService.runManual(fileName, force)));
    }

    @Operation(summary = "수신함 파일 목록과 처리 상태",
            description = "/data/csv 의 product_master_YYYYMMDD.csv 를 최신 날짜 먼저 보여 줍니다. 파일마다 마지막 처리 상태 · 시각 · 실패 사유 · 건수. " +
                    "PENDING=아직 처리 전인 최신 파일, SUPERSEDED=처리한 적 없는데 더 새 파일이 있어 건너뛴 파일. 이름 규칙에 맞지 않는 파일은 ignoredFiles.")
    @GetMapping
    public ResponseEntity<CommonResponse<InboxListResponse>> list() {
        return ResponseEntity.ok(CommonResponse.success(inboxService.list()));
    }
}
