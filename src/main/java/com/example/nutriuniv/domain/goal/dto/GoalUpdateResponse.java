package com.example.nutriuniv.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * PUT /me/goal 출력 — 전/후/이유를 한 응답에 (추가 조회 금지, 기능명세서 2.4).
 * recalculation 은 recalcProductId 가 없거나 그 제품에 등급이 없으면(INSUFFICIENT) null → 화면 스킵.
 */
@Getter
@Builder
public class GoalUpdateResponse {

    private String goal;
    private String label;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime appliedAt;

    private Recalculation recalculation;

    @Getter
    @Builder
    public static class Recalculation {
        private Long productId;
        private String name;
        private Grade before;
        private Grade after;
        private boolean changed;   // false(변화 없음)도 정상 — 「그대로예요」 표시용
        private String reason;
    }

    @Getter
    @Builder
    public static class Grade {
        private String grade;
        private String label;
    }
}
