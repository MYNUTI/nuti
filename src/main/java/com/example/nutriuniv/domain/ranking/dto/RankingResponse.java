package com.example.nutriuniv.domain.ranking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /rankings 출력 (기능명세서 6.3) — 순위와 등급을 반드시 함께. 「초콜릿 1위인데 D등급」이 그대로 보인다. */
@Getter
@Builder
public class RankingResponse {

    private Long categoryId;
    private String categoryName;
    private String appliedGoal;
    private Coverage coverage;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private long totalRanked;               // 저장된 순위 수 (분류당 상한 app.ranking.max-rank)
    private int page;
    private int size;
    private boolean hasNext;
    private List<Item> items;

    @Getter
    @Builder
    public static class Coverage {
        private long totalCount;            // 「단백질 음료 128개 중」
        private long analyzedCount;         // 「121개 분석 완료」
        private double ratio;               // 0.95
    }

    @Getter
    @Builder
    public static class Item {
        private int rank;
        private Long productId;
        private String name;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String brandName;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String imageUrl;

        private String grade;
        private String gradeLabel;
        private List<String> highlights;    // 목표 기준 위반 문구 (없으면 빈 배열)

        @JsonProperty("isSaved")
        private boolean isSaved;
    }
}
