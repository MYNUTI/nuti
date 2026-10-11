package com.example.nutriuniv.domain.grade.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /grades/guide 출력 (기능명세서 4.1 「화면에 보여줄 등급 설명 문구도 서버가 준다」).
 * 재산출(4.2)로 기준이 바뀌면 클라 배포 없이 여기 내용이 바뀐다.
 */
@Getter
@Builder
public class GradeGuideResponse {

    private String version;                     // 현행 기준 버전 (grade_recalibrations.version)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;            // 적용 시점

    private String appliedGoal;

    private List<Section> sections;             // 산정 단계 · 성분별 비중(기준값 포함) · 목표별 배점 차이 · 식이섬유 안내

    private List<GradeScale> gradeScale;        // A~E 라벨·설명

    @Getter
    @Builder
    public static class Section {
        private String title;
        private String body;
    }

    @Getter
    @Builder
    public static class GradeScale {
        private String grade;
        private String label;
        private String description;
    }
}
