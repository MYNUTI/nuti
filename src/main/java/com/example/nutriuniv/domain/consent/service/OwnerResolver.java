package com.example.nutriuniv.domain.consent.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import org.springframework.stereotype.Service;

/**
 * 요청 주체(Actor) → 개인 귀속 데이터의 소유자(Owner).
 *
 * <p>순서: 로그인(JWT) → 기기 익명 ID(X-Anonymous-Id) → 둘 다 없으면 400 ANONYMOUS_ID_REQUIRED.
 * v3(ACCT_001): 기기 ID 는 기기가 처음 열릴 때 만들고 동의 없이 모든 기능에 쓴다 — 서버가 발급하거나 등록 여부를 확인하지 않는다.
 * 모양 검사(UUID)는 ActorArgumentResolver 가 이미 했다.
 *
 * <p>로그인으로 계정에 옮긴 뒤에도 같은 기기 ID 를 계속 쓰면 그 뒤 쌓인 기록은 그 ID 소유로 남고, 다음 로그인 때 다시 옮겨진다.
 * 저장·목표·기여 서비스가 모두 이걸 거친다.
 */
@Service
public class OwnerResolver {

    /** 소유자 확정. 없으면 400 ANONYMOUS_ID_REQUIRED. */
    public Owner resolve(Actor actor) {
        Owner owner = resolveOrNull(actor);
        if (owner == null) {
            throw new CustomException(ErrorCode.ANONYMOUS_ID_REQUIRED);
        }
        return owner;
    }

    /** 소유자가 있으면 반환, 없으면 null — 조회성 API 에서 "미설정 → 기본값" 처리용. */
    public Owner resolveOrNull(Actor actor) {
        if (actor.isLoggedIn()) {
            return Owner.ofUser(actor.userId());
        }
        if (actor.hasAnonymousId()) {
            return Owner.ofAnonymous(actor.anonymousId());
        }
        return null;
    }
}
