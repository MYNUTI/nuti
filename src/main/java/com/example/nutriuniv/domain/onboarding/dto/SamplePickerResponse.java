package com.example.nutriuniv.domain.onboarding.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** GET /onboarding/samples 출력 (기능명세서 2.2) — 등급은 일반 기준. A·D 각 1개 포함. */
@Getter
@Builder
public class SamplePickerResponse {

    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private Long productId;
        private String name;
        private String imageUrl;
        private String grade;
    }
}
