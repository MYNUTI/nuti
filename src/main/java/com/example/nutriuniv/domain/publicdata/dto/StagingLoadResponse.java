package com.example.nutriuniv.domain.publicdata.dto;

import java.util.List;

/**
 * POST /admin/product-sync/staging 결과.
 * hashMismatchCount 가 0 이 아니면 서버 계산기(ContentHashCalculator)와 파이프라인 build.py 의 hash 가 다르다는 뜻 — 반영 전에 원인을 본다.
 */
public record StagingLoadResponse(
        int stagedCount,             // 복제 테이블에 넣은 행 수
        int duplicateCount,          // 같은 master_id 가 다시 나와 건너뛴 행
        int missingMasterIdCount,    // master_id 가 비어 건너뛴 행
        int hashMismatchCount,       // 파일의 content_hash 와 서버 계산값이 다른 행
        List<String> hashMismatchSamples   // 다른 행의 master_id 예시 (최대 20개)
) {}