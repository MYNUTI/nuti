package com.example.nutriuniv.domain.ranking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /admin/rankings/categories — 활성 서비스 분류 전부(게이트 미통과 포함)와 미통과 이유. 매핑을 넣은 뒤 어디가 왜 안 열리는지 본다. */
@Getter
@Builder
public class AdminRankingCategoriesResponse {

    private List<Item> items;
    private int maxRank;                    // 분류당 저장 상한 (app.ranking.max-rank)
    private boolean rebuildRunning;

    @Getter
    @Builder
    public static class Item {
        private Long categoryId;
        private String name;
        private int displayOrder;

        @JsonProperty("isDefault")
        private boolean isDefault;

        private long mappingCount;          // 매핑된 categories 행 수 (하위 분류는 자동 포함되므로 보통 작다)
        private long totalCount;
        private long analyzedCount;
        private long gradeACount;
        private long gradeDCount;
        private boolean gatePassed;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String gateFailureReason;

        private int rankedRows;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime computedAt;   // null = 아직 배치가 돌지 않음
    }
}
