package com.example.nutriuniv.domain.publicdata.dto;

import java.util.List;

/** POST /admin/product-sync/apply 결과 — 건수는 제품(master_id) 단위. */
public record SyncApplyResponse(
        Long loadId,
        long stagedCount,            // 복제 테이블 전체 행
        int candidateCount,          // hash 가 다르거나 새로 생겼거나 다시 나타나 살펴본 행
        int createdCount,            // 새 제품
        int updatedCount,            // 값이 바뀐 제품
        int unchangedCount,          // 반영할 것이 없던 제품 (stagedCount 기준)
        int protectedCount,          // 관리자가 고친 제품이라 건너뜀
        int reactivatedCount,        // 최신화가 비활성으로 돌렸다가 원천에 다시 나타나 살린 제품
        int deactivatedCount,        // 원천에서 사라져 비활성으로 돌린 제품
        int disappearedCount,        // 원천에서 사라진 제품 (비활성 대상)
        boolean deactivationSkipped, // 사라진 제품이 너무 많아 비활성을 건너뛰었는가
        int failCount,
        List<FailItem> failItems     // 최대 100개
) {
    public record FailItem(String masterId, String reason) {}
}