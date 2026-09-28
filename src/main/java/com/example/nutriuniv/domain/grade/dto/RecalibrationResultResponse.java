package com.example.nutriuniv.domain.grade.dto;

import com.example.nutriuniv.domain.grade.entity.GradeRecalibration;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** POST /admin/grade-recalibrations 출력 — 새 버전이 적용됐음을 알린다. */
@Getter
@Builder
public class RecalibrationResultResponse {

    private Long recalibrationId;
    private String version;
    private String status;          // APPLIED
    private Integer targetCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime appliedAt;

    public static RecalibrationResultResponse from(GradeRecalibration r) {
        return RecalibrationResultResponse.builder()
                .recalibrationId(r.getId())
                .version(r.getVersion())
                .status(r.getStatus().name())
                .targetCount(r.getTargetCount())
                .appliedAt(r.getAppliedAt())
                .build();
    }
}
