package com.example.nutriuniv.domain.consent.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 주체(Actor) → 개인 귀속 데이터의 소유자(Owner).
 *
 * <p>순서: 로그인(JWT) → 서버가 발급했고 아직 병합되지 않은 익명 ID → 둘 다 아니면 403 CONSENT_REQUIRED
 * (클라이언트는 동의 화면을 띄운 뒤 원래 동작을 이어간다 — 기능명세서 1.2).
 * 헤더에 아무 문자열이나 넣어도 소유자가 되지 않는다 — anonymous_users 에 있는 값만 인정.
 * 저장·목표·동의·기여 서비스가 모두 이걸 거친다.
 */
@Service
@RequiredArgsConstructor
public class OwnerResolver {

    private final AnonymousUserRepository anonymousUserRepository;

    /** 소유자 확정. 없으면 403 CONSENT_REQUIRED. */
    @Transactional(readOnly = true)
    public Owner resolve(Actor actor) {
        Owner owner = resolveOrNull(actor);
        if (owner == null) {
            throw new CustomException(ErrorCode.CONSENT_REQUIRED);
        }
        return owner;
    }

    /** 소유자가 있으면 반환, 없으면 null — 조회성 API 에서 "미설정 → 기본값" 처리용. */
    @Transactional(readOnly = true)
    public Owner resolveOrNull(Actor actor) {
        if (actor.isLoggedIn()) {
            return Owner.ofUser(actor.userId());
        }
        if (actor.hasAnonymousId()) {
            AnonymousUser anon = anonymousUserRepository.findByAnonymousId(actor.anonymousId()).orElse(null);
            if (anon != null && !anon.isMerged()) {
                return Owner.ofAnonymous(anon.getAnonymousId());
            }
        }
        return null;
    }
}
