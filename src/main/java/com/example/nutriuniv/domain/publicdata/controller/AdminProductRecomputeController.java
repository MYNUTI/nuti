package com.example.nutriuniv.domain.publicdata.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.domain.publicdata.dto.BrandGroupLoadResponse;
import com.example.nutriuniv.domain.publicdata.dto.RecomputeResponse;
import com.example.nutriuniv.domain.publicdata.service.BrandGroupLoader;
import com.example.nutriuniv.domain.publicdata.service.ProductRecomputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Admin · Product Sync", description = "공공데이터 최신화 — 34컬럼 재계산")
@RestController
@RequestMapping("/admin/product-sync")
@RequiredArgsConstructor
public class AdminProductRecomputeController {

    private final ProductRecomputeService recomputeService;
    private final BrandGroupLoader brandGroupLoader;

    @Operation(summary = "재계산할 수 있는지", description = "막는 이유 목록. 비어 있으면 재계산할 수 있다(API 11개 전량 완료 · 받는 중/실패 없음 · 브랜드 조회표 준비).")
    @GetMapping("/recompute/blockers")
    public ResponseEntity<CommonResponse<List<String>>> blockers() {
        return ResponseEntity.ok(CommonResponse.success(recomputeService.blockers()));
    }

    @Operation(summary = "34컬럼 재계산",
            description = "원본 표 전부로 34컬럼을 다시 계산해 복제 테이블을 채운다(기존 복제 테이블은 지움). 실제 product 반영은 /preview → /apply. " +
                    "분류(category)는 지금 product 의 값을 이어 쓰고, 새 제품은 비워 둔다. 막는 이유가 있으면 409.")
    @PostMapping("/recompute")
    public ResponseEntity<CommonResponse<RecomputeResponse>> recompute() {
        return ResponseEntity.ok(CommonResponse.success(recomputeService.recompute()));
    }

    @Operation(summary = "묶음 구성표 올리기",
            description = "통합 마스터의 묶음으로 brand_groups 를 통째로 바꾼다. 헤더: food_cd,group_key,is_representative(Y/N). " +
                    "쿠팡 보정 · 11-12 고침이 이 묶음 키에 걸려 있어, 올리지 않으면 그 값들이 들어가지 않는다.")
    @PostMapping(value = "/brand-groups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<BrandGroupLoadResponse>> loadBrandGroups(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(CommonResponse.success(brandGroupLoader.load(file)));
    }
}