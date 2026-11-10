package com.example.nutriuniv.domain.consent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/** GET /me/consents · PUT /me/consents/{item} 출력 — 항목 6개의 지금 상태(마지막 기록). 기록이 없으면 agreed=false · changedAt=null. */
public record ConsentsResponse(List<Item> items) {

    public record Item(
            String item,
            String label,
            boolean required,
            boolean agreed,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime changedAt,
            String termsVersion,
            String privacyVersion,
            String documentVersion) {}
}
