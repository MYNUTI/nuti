package com.example.nutriuniv.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** GET /admin/dashboard/data-status 출력 (기능명세서 10.5). 등급 분포는 재산출 필요 신호, 분류별 비율은 랭킹 게이트 통과 여부와 함께. */
@Getter
@Builder
public class DataStatusResponse {

    private long totalProducts;
    private long analyzedCount;
    private double analyzedRatio;
    private long barcodeCount;
    private double barcodeRatio;
    private Map<String, Long> gradeDistribution;            // 일반 기준 A~E

    private List<CategoryRatio> categoryAnalyzedRatios;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLoadedAt;

    private long pendingRequestCount;                       // 분석 대기 WAITING + PROCESSING

    @Getter
    @Builder
    public static class CategoryRatio {
        private Long categoryId;
        private String name;
        private long totalCount;
        private long analyzedCount;
        private double ratio;
        private long gradeACount;
        private long gradeDCount;
        private boolean rankingGatePassed;                  // 분석 완료 300건 이상 + A·D 각 1건 이상 (6.3)
    }
}
