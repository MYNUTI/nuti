package com.example.nutriuniv.domain.consent.entity;

/** 동의 종류. 개인정보(1.2)와 건강정보(1.3)는 체크박스·저장 위치를 분리한다(기능명세서). */
public enum ConsentType {
    PRIVACY,
    HEALTH
}
