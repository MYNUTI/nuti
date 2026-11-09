package com.example.nutriuniv.common.security;

/**
 * 요청을 보낸 주체. 셋 중 하나다.
 * <ul>
 *   <li>로그인 유저 — JWT 로 인증됨 (userId 있음)</li>
 *   <li>기기 사용자 — 기기가 처음 열릴 때 만든 UUID 를 X-Anonymous-Id 헤더로 보낸다 (anonymousId 있음). 동의 없이 모든 기능을 쓴다</li>
 *   <li>둘 다 없음 — 헤더를 안 보낸 요청. 조회성 API 만 가능, 개인 귀속 API 는 400 ANONYMOUS_ID_REQUIRED</li>
 * </ul>
 * 컨트롤러 파라미터에 {@code Actor} 를 선언하면 {@link ActorArgumentResolver} 가 채워 넣는다.
 * 명세 공통 규칙: 로그인·비로그인이 같은 API 를 쓰고 소유자만 다르다. 둘 다 있으면 계정 우선.
 *
 * <p>sessionId·cohort 는 로깅 인프라(X-Session-Id·X-Cohort)용 부가 정보. ipAddress 는 동의 기록의 해시용으로만 쓰고 원문은 저장하지 않는다.
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

    /** 로그인이든 기기 ID 든 '소유자'로 귀속시킬 수 있는가 (헤더 없는 요청은 false). */
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
