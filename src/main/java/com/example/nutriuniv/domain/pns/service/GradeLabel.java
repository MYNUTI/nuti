package com.example.nutriuniv.domain.pns.service;

import java.util.Map;

/**
 * 등급 문자(A~E) → 화면 라벨. 문구 원칙: 「나쁩니다」 대신 「잘 안 맞아요」 (기능명세서 5.1).
 * 등급 엔진 개조 시 grade_cutoffs 테이블의 label 로 대체된다 — 그때까지의 고정값.
 */
public final class GradeLabel {

    private static final Map<String, String> LABELS = Map.of(
            "A", "아주 잘 맞아요",
            "B", "잘 맞아요",
            "C", "보통이에요",
            "D", "조금 아쉬워요",
            "E", "잘 안 맞아요"
    );

    private GradeLabel() {
    }

    public static String of(String grade) {
        return grade == null ? null : LABELS.get(grade);
    }
}
