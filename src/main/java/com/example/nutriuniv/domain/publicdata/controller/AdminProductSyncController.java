package com.example.nutriuniv.domain.publicdata.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.publicdata.dto.StagingLoadResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncPreviewResponse;
import com.example.nutriuniv.domain.publicdata.service.ProductStagingLoader;
import com.example.nutriuniv.domain.publicdata.service.ProductSyncApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin · Product Sync", description = "공공데이터 제품 최신화 (복제 테이블 → 실제 product)")
@RestController
@RequestMapping("/admin/product-sync")
@RequiredArgsConstructor
public class AdminProductSyncController {

    private final ProductStagingLoader stagingLoader;
    private final ProductSyncApplyService applyService;

    @Operation(summary = "복제 테이블 채우기 (34컬럼 CSV)",
            description = "데이터 팀 product_master.csv 형식(UTF-8)으로 복제 테이블을 통째로 바꿉니다. 실제 product 는 건드리지 않습니다. " +
                    "content_hash 는 서버가 다시 계산해 넣고, 파일 값과 다르면 hashMismatchCount 와 예시를 돌려줍니다. " +
                    "같은 master_id 는 첫 행만 넣습니다. 적재·반영 중이면 409.")
    @PostMapping(value = "/staging", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<StagingLoadResponse>> loadStaging(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(CommonResponse.success(stagingLoader.load(file)));
    }

    @Operation(summary = "반영 미리 보기",
            description = "복제 테이블을 실제 product 에 반영하면 새로 만들 · 갱신할 · 다시 살릴 · 비활성으로 돌릴 제품 수를 셉니다. 아무것도 바꾸지 않습니다.")
    @GetMapping("/preview")
    public ResponseEntity<CommonResponse<SyncPreviewResponse>> preview() {
        return ResponseEntity.ok(CommonResponse.success(applyService.preview()));
    }

    @Operation(summary = "반영",
            description = "content_hash 가 다르거나 새로 생긴 제품만 실제 product 에 반영합니다(관리자가 고친 제품은 건너뜀). " +
                    "원천에서 사라진 제품은 비활성으로 돌립니다 — 복제 테이블에 들어 있는 출처(source)의 제품만 대상이고 다른 출처(올리브영 등)는 건드리지 않습니다. " +
                    "출처마다 사라진 제품이 그 출처 적재 제품의 10%를 넘으면 수집이 덜 된 것으로 보고 그 출처만 건너뜁니다(sources 에 출처별 결과). " +
                    "바뀐 것이 있으면 등급을 백그라운드로 다시 계산합니다. 복제 테이블이 비었거나 다른 적재·반영 중이면 409.")
    @PostMapping("/apply")
    public ResponseEntity<CommonResponse<SyncApplyResponse>> apply(
            @Parameter(description = "사라진 제품이 10%를 넘어도 비활성으로 돌릴지 (기본 false)")
            @RequestParam(defaultValue = "false") boolean allowMassDeactivation) {
        return ResponseEntity.ok(CommonResponse.success(applyService.apply(allowMassDeactivation)));
    }
}