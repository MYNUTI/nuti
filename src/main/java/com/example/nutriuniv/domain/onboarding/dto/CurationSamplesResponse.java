package com.example.nutriuniv.domain.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** GET /admin/curation/samples 출력 (기능명세서 10.1) — 등급은 조회 시 다시 계산해 보여준다(재산출 반영). */
@Getter
@Builder
public class CurationSamplesResponse {

    private List<Item> items;
    private long activeCount;
    private long gradeACount;
    private List<String> warnings;

    @Getter
    @Builder
    public static class Item {
        private Long productId;
        private String name;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String grade;                   // 일반 기준. 영양정보 부족이면 null

        private int displayOrder;

        @JsonProperty("isActive")
        private boolean active;
    }
}
