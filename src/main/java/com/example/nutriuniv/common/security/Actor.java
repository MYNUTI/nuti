package com.example.nutriuniv.common.security;

/**
 * 요청을 보낸 주체. 셋 중 하나다.
 * <ul>
 *   <li>로그인 유저 — JWT 로 인증됨 (userId 있음)</li>
 *   <li>익명 사용자 — 개인정보 동의 시 서버가 발급한 X-Anonymous-Id 헤더 (anonymousId 있음)</li>
 *   <li>둘 다 없음 — 동의 전 사용자. 조회성 API 만 가능, 개인 귀속 API 는 403 CONSENT_REQUIRED</li>
 * </ul>
 * 컨트롤러 파라미터에 {@code Actor} 를 선언하면 {@link ActorArgumentResolver} 가 채워 넣는다.
 * 명세 공통 규칙: 로그인·비로그인이 같은 API 를 쓰고 소유자만 다르다. 둘 다 있으면 계정 우선.
 *
 * <p>sessionId·cohort·ipAddress 는 로깅 인프라(X-Session-Id·X-Cohort·X-Forwarded-For)용 부가 정보.
 */
public record Actor(
        Long userId,
        String role,
        String anonymousId,
        String sessionId,
        String cohort,
        String ipAddress
) {
    public boolean isLoggedIn() {
        return userId != null;
    }

    public boolean hasAnonymousId() {
        return anonymousId != null;
    }

    /** 로그인이든 익명이든 '소유자'로 귀속시킬 수 있는가 (동의 전 사용자는 false). */
    public boolean canOwn() {
        return isLoggedIn() || hasAnonymousId();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** X-Cohort 값을 warm/cold 로 정규화. 그 외 값/누락은 null. */
    public static String normalizeCohort(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        return ("warm".equals(v) || "cold".equals(v)) ? v : null;
    }
}
