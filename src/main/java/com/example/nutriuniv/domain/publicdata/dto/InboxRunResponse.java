package com.example.nutriuniv.domain.publicdata.dto;

/**
 * POST /admin/product-sync/inbox/run — 처리를 시작한 파일. 처리는 뒤에서 돌고, 결과는 GET /admin/product-sync/inbox 로 본다.
 */
public record InboxRunResponse(
        Long historyId,
        String fileName,
        String status   // PROCESSING
) {}
