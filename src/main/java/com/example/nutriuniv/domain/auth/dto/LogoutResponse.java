package com.example.nutriuniv.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** POST /auth/logout 출력 — 새로 발급한 익명 ID. 클라이언트는 기존(병합된) ID 를 버리고 이 값을 localStorage 에 둔다. */
@Getter
@AllArgsConstructor
public class LogoutResponse {
    private String anonymousId;
}
