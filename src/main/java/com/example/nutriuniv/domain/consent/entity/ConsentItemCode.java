package com.example.nutriuniv.domain.consent.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 가입 동의 항목 (v3 ACCT_004) — 필수 3 · 선택 3. 「데이터 2차 활용 · 제공」은 11-05 에 빠졌다.
 * 선택 항목만 나중에 켜고 끌 수 있다(ACCT_007). 철회하면 그 동의로 저장한 데이터를 바로 지운다.
 */
public enum ConsentItemCode {
    AGE_14("만 14세 이상", true),
    TERMS("이용약관", true),
    PRIVACY("개인정보 수집·이용", true),
    GOAL_ACCOUNT("목표 계정 저장", false),       // 민감정보(건강 목표) — 동의해야 목표를 계정에 저장
    BODY_PROFILE("몸 정보 수집·이용", false),     // 민감정보 — 동의해야 몸 정보 · 칼로리 구간을 계정에 저장
    MARKETING("광고성 정보 수신", false);

    private final String label;
    private final boolean required;

    ConsentItemCode(String label, boolean required) {
        this.label = label;
        this.required = required;
    }

    public String label() {
        return label;
    }

    public boolean required() {
        return required;
    }

    public static ConsentItemCode from(String raw) {
        if (raw != null) {
            for (ConsentItemCode c : values()) {
                if (c.name().equalsIgnoreCase(raw.trim())) return c;
            }
        }
        throw new CustomException(ErrorCode.BAD_REQUEST,
                "동의 항목은 AGE_14 · TERMS · PRIVACY · GOAL_ACCOUNT · BODY_PROFILE · MARKETING 중 하나여야 합니다.");
    }
}
