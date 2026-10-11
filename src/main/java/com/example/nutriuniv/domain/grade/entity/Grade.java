package com.example.nutriuniv.domain.grade.entity;

/** 등급 문자 A~E (기능명세서 4.1 ⑤). 순서가 좋은 쪽 → 나쁜 쪽. */
public enum Grade {
    A, B, C, D, E;

    /** "A" 같은 문자열 → 등급. 없는 값은 null (등급 미계산 제품). */
    public static Grade fromString(String raw) {
        if (raw == null) return null;
        try {
            return Grade.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
