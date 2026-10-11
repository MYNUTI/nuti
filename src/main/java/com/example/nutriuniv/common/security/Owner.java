package com.example.nutriuniv.common.security;

/**
 * 개인 귀속 데이터(저장·목표·동의·기여)의 소유자. user_id XOR anonymous_id — 정확히 하나만 채워진다.
 * Actor 에서 OwnerResolver 가 확정한다(로그인 우선, 그다음 서버가 발급한 익명 ID, 둘 다 없으면 403).
 */
public record Owner(Long userId, String anonymousId) {

    public static Owner ofUser(Long userId) {
        return new Owner(userId, null);
    }

    public static Owner ofAnonymous(String anonymousId) {
        return new Owner(null, anonymousId);
    }

    public boolean isUser() {
        return userId != null;
    }
}
