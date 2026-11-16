package com.example.nutriuniv.domain.publicdata.dto;

/** GET /admin/product-sync/preview — 반영하면 무엇이 바뀌는지 미리 센다(아무것도 바꾸지 않음). */
public record SyncPreviewResponse(
        long stagedCount,            // 복제 테이블 전체 행
        long createCount,            // 새로 만들 제품
        long updateCount,            // hash 가 달라 갱신 후보인 제품 (관리자가 고친 제품 포함 — 반영 때 보호로 건너뜀)
        long reactivateCount,        // 다시 살릴 제품
        long disappearedCount,       // 원천에서 사라진 제품
        long activeLoadedCount,      // 적재로 들어온 활성 제품 수 (비율 기준)
        boolean massDeactivation     // 사라진 제품이 기준(10%)을 넘는가 — 넘으면 반영 때 비활성을 건너뛴다
) {}