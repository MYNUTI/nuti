package com.example.nutriuniv.domain.grade.dto;

import com.example.nutriuniv.domain.grade.entity.GradeRecalibration;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** GET /admin/grade-recalibrations 출력 — 적용 시점 기록(전후로 같은 제품 등급이 바뀜). 최신순. */
@Getter
@Builder
public class RecalibrationHistoryResponse {

    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private Long recalibrationId;
        private String version;
        private String status;          // APPLIED | ROLLED_BACK

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate baseDate;

        private Integer sampleCount;    // 모수 (target_count)

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime appliedAt;

        private String memo;

        public static Item from(GradeRecalibration r) {
            return Item.builder()
                    .recalibrationId(r.getId())
                    .version(r.getVersion())
                    .status(r.getStatus().name())
                    .baseDate(r.getBaseDate())
                    .sampleCount(r.getTargetCount())
                    .appliedAt(r.getAppliedAt())
                    .memo(r.getMemo())
                    .build();
        }
    }
}
