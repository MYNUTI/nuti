package com.example.nutriuniv.domain.consent.dto;

import java.util.List;

/** GET /meta/consent-items — 가입 동의 화면에 그릴 항목 6개(필수 3 · 선택 3)와 지금 약관 · 처리방침 판. */
public record ConsentItemsResponse(String termsVersion, String privacyVersion, List<Item> items) {

    public record Item(String item, String label, boolean required) {}
}
