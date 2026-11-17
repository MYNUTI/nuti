package com.example.nutriuniv.domain.publicdata.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 34컬럼 재계산 결과 — POST /admin/product-sync/recompute. 복제 테이블을 채운 뒤의 숫자다(반영은 따로). */
public record RecomputeResponse(
        int stagingRows,            // 복제 테이블에 넣은 행
        int nutriDbRows,            // 영양성분DB 행 (가공식품 · 상용제품)
        int foodQrRows,             // 푸드QR 행
        int foodQrExcluded,         // 영양성분DB 와 보고번호 · 바코드가 겹쳐 뺀 푸드QR 행
        int withBarcode,
        int barcodeByName,          // 보고번호로 못 찾아 제품명 + 제조사로 찾은 바코드
        int barcodeCleared,         // 한 바코드가 여러 행에 걸려 비운 행
        int withIngredients,
        int discontinued,
        int withBrand,
        int withCategory,
        int newProducts,            // 지금 product 에 없는 master_id
        int newGroups,              // 새로 만든 묶음
        int newWithoutCategoryCount,
        List<String> newWithoutCategorySamples,   // 분류가 없는 새 제품 master_id (앞 20개)
        int placeNames,             // 지명 목록 크기 (시도 17개 포함)
        long elapsedMs,
        LocalDateTime computedAt
) {}