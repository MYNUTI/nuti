package com.example.nutriuniv.domain.publicdata.dto;

import java.util.List;

/** GET /admin/product-sync/preview — 반영하면 무엇이 바뀌는지 미리 센다(아무것도 바꾸지 않음). */
public record SyncPreviewResponse(
        long stagedCount,            // 대기 테이블 전체 행
        long createCount,            // 새로 만들 제품
        long updateCount,            // hash 가 달라 갱신 후보인 제품 (관리자가 고친 제품 포함 — 반영 때 보호로 건너뜀)
        long reactivateCount,        // 다시 살릴 제품
        long disappearedCount,       // 원천에서 사라진 제품 (대기 테이블이 맡는 출처만)
        long activeLoadedCount,      // 대기 테이블이 맡는 출처의 활성 적재 제품 수
        boolean massDeactivation,    // 어느 한 출처라도 사라진 제품이 10%를 넘는가 — 넘는 출처는 반영 때 비활성을 건너뛴다
        List<SourceDeactivation> sources   // 출처별 사라진 제품 · 10% 초과 여부
) {}