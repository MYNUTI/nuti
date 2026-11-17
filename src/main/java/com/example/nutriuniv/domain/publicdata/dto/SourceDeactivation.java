package com.example.nutriuniv.domain.publicdata.dto;

/**
 * 출처(source) 하나의 「원천에서 사라진 제품」 처리 결과. 대기 테이블에 들어 있는 출처만 나온다.
 * skipped 면 사라진 제품이 그 출처 적재 제품의 10%를 넘어 수집이 덜 된 것으로 보고 비활성을 건너뛴 것.
 */
public record SourceDeactivation(
        String source,
        long activeLoadedCount,   // 이 출처의 활성 적재 제품 수
        int disappearedCount,     // 이 출처에서 대기 테이블에 없는 제품 수
        int deactivatedCount,     // 실제로 비활성으로 돌린 수 (미리 보기에서는 0)
        boolean skipped           // 10%를 넘어 건너뛰었는가 (미리 보기에서는 넘는가)
) {}