package com.example.nutriuniv.domain.consent.entity;

/** 동의 기록 행의 성격. 철회도 새 행으로 남긴다(append-only). */
public enum ConsentAction {
    AGREE,
    REVOKE
}
