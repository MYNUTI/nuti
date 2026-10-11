package com.example.nutriuniv.domain.ranking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /rankings/criteria 출력 — 목록 위 [선정 기준] 링크가 여는 문구. DB(grade_copies RANKING_*)에서 읽어 클라 배포 없이 갱신. */
@Getter
@Builder
public class RankingCriteriaResponse {

    private String version;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private List<Section> sections;

    @Getter
    @Builder
    public static class Section {
        private String title;
        private String body;
    }
}
