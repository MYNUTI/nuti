package com.example.nutriuniv.domain.publicdata.dto;

import java.util.List;

/** POST /admin/product-sync/apply 결과 — 건수는 제품(master_id) 단위. */
public record SyncApplyResponse(
        Long loadId,
        long stagedCount,            // 대기 테이블 전체 행
        int candidateCount,          // hash 가 다르거나 새로 생겼거나 다시 나타나 살펴본 행
        int createdCount,            // 새 제품
        int updatedCount,            // 값이 바뀐 제품
        int unchangedCount,          // 반영할 것이 없던 제품 (stagedCount 기준)
        int protectedCount,          // 관리자가 고친 제품이라 건너뜀
        int reactivatedCount,        // 현행 아님이었다가 원천에 다시 나타나 현행으로 되돌린 제품
        int deactivatedCount,        // 원천에서 사라져 현행 아님으로 돌린 제품 (전 출처 합) — 검색 · 스캔에는 남음
        int disappearedCount,        // 원천에서 사라진 제품 (대기 테이블이 맡는 출처만, 전 출처 합)
        boolean deactivationSkipped, // 어느 한 출처라도 10%를 넘어 현행 아님 처리를 건너뛰었는가
        List<SourceDeactivation> sources,   // 출처별 결과
        int failCount,
        List<FailItem> failItems     // 최대 100개
) {
    public record FailItem(String masterId, String reason) {}
}