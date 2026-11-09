package com.example.nutriuniv.domain.history.entity;

/** 결과 화면에 들어온 경로 (v3 RTN_001). 모르는 값 · 없음은 OTHER — 기록 때문에 조회가 실패하지 않게. */
public enum ViewSource {
    BARCODE, SEARCH, RANKING, HOME, HISTORY, SAVED, ALTERNATIVE, COMPARE, OTHER;

    public static ViewSource from(String raw) {
        if (raw == null || raw.isBlank()) return OTHER;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
