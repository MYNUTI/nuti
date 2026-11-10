package com.example.nutriuniv.domain.consent.dto;

import com.example.nutriuniv.domain.body.dto.BodyProfileRequest;

/**
 * PUT /me/consents/{item} 입력 (ACCT_007) — 선택 항목만.
 * 켤 때: GOAL_ACCOUNT 는 goal(기기 목표)을 함께 보내면 계정에 저장하고, BODY_PROFILE 은 bodyProfile(기기 값)을 함께 보내면 저장한다.
 */
public record ConsentChangeRequest(
        Boolean agreed,
        String documentVersion,     // 동의문 판 (몸 정보 · 목표 저장)
        String goal,                // GOAL_ACCOUNT 켤 때 — WEIGHT_LOSS | MUSCLE_GAIN
        BodyProfileRequest bodyProfile
) {}
