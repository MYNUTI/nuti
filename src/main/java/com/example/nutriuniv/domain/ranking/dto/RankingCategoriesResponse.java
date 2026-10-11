package com.example.nutriuniv.domain.ranking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /rankings/categories 출력 (기능명세서 6.3) — 게이트를 통과한 서비스 분류만. 비어 있으면 클라이언트는 랭킹 탭을 숨긴다. */
@Getter
@Builder
public class RankingCategoriesResponse {

    private List<Item> items;

    /** 마지막 배치 시각. 결과가 하나도 없으면 null. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class Item {
        private Long categoryId;
        private String name;
        private long productCount;          // 분모 — 영양정보 부족 제품 포함
        private long analyzedCount;
        private double analyzedRatio;

        @JsonProperty("isDefault")
        private boolean isDefault;          // 처음 선택되는 탭 — 항목 중 정확히 하나
    }
}
