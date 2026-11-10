package com.example.nutriuniv.domain.auth.dto;

import java.util.Map;

/**
 * POST /auth/signup 입력 (v3 ACCT_004) — 가입 동의 화면(5-6)의 「동의하고 시작하기」.
 * agreements: 항목 코드 → 동의 여부. 필수 AGE_14 · TERMS · PRIVACY 가 모두 true 여야 한다. 빠진 선택 항목은 false 로 기록.
 * goal: GOAL_ACCOUNT 에 동의했을 때 기기 목표(WEIGHT_LOSS | MUSCLE_GAIN) — 계정에 저장한다.
 */
public record SignupRequest(
        String signupToken,
        Map<String, Boolean> agreements,
        String termsVersion,
        String privacyVersion,
        Map<String, String> documentVersions,   // 항목 코드 → 동의문 판 (BODY_PROFILE 등)
        String goal
) {}
