package com.example.nutriuniv.domain.publicdata.dto;

import java.util.List;

/** 재계산 → 반영 한 번 돌리기 결과. 돌지 않았으면 skippedReasons 에 이유가 있고 나머지는 null. */
public record SyncRunResponse(
        boolean ran,
        List<String> skippedReasons,
        RecomputeResponse recompute,
        SyncApplyResponse apply
) {}