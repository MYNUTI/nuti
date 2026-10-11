package com.example.nutriuniv.domain.logging.dto;

import com.example.nutriuniv.common.security.Actor;

/**
 * 모든 로깅 이벤트의 공통 식별 컨텍스트.
 * 클라이언트가 HTTP 헤더로 전달하는 값(anonymousId/sessionId/cohort)과
 * 서버가 해석한 값(userId/ipAddress)을 한데 묶는다.
 *
 * <p>비로그인 사용자도 anonymousId(localStorage UUID)로 재방문이 추적된다.
 * IP는 식별자가 아닌 약한 보조 용도로만 보관한다.
 */
public record LogContext(
        String anonymousId,
        String sessionId,
        String cohort,
        Long userId,
        String ipAddress
) {
    /** 요청 주체(Actor)에서 로깅 컨텍스트로 — 필드 의미 동일, 헤더 해석은 ActorArgumentResolver 가 이미 끝냄. */
    public static LogContext from(Actor actor) {
        return new LogContext(actor.anonymousId(), actor.sessionId(), actor.cohort(), actor.userId(), actor.ipAddress());
    }

    /**
     * cohort 값을 warm/cold로 정규화한다. 그 외 값/누락은 null.
     */
    public static String normalizeCohort(String raw) {
        return Actor.normalizeCohort(raw);
    }
}
