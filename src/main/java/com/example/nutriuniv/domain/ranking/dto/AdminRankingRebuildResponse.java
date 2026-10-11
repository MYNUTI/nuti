package com.example.nutriuniv.domain.ranking.dto;

import com.example.nutriuniv.domain.ranking.service.RankingBatchService;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** POST /admin/rankings/rebuild 출력. */
@Getter
@Builder
public class AdminRankingRebuildResponse {

    private int categories;
    private int opened;
    private int closed;
    private int rows;
    private long elapsedMs;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startedAt;

    public static AdminRankingRebuildResponse from(RankingBatchService.RebuildResult r) {
        return AdminRankingRebuildResponse.builder()
                .categories(r.categories())
                .opened(r.opened())
                .closed(r.closed())
                .rows(r.rows())
                .elapsedMs(r.elapsedMs())
                .startedAt(r.startedAt())
                .build();
    }
}
