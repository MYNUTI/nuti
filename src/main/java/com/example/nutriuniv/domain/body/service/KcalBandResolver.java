package com.example.nutriuniv.domain.body.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 등급 계산에 쓸 칼로리 구간 (v3 CORE_008 · 11-11).
 * 몸 정보에 동의하고 값을 저장한 계정이면 저장된 구간, 그 밖(로그인 전 · 동의 안 함 · 저장 전)은 2,000.
 * 요청에 칼로리 구간이 실려도 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class KcalBandResolver {

    private final BodyProfileService bodyProfileService;

    public EerBand resolve(Actor actor) {
        if (actor != null && actor.isLoggedIn()) {
            EerBand stored = bodyProfileService.storedBand(actor.userId());
            if (stored != null) return stored;
        }
        return EerBand.DEFAULT;
    }

    /** 로그인 유저 id 기준 (목록 · 추천처럼 Actor 를 받지 않는 호출부). null 이면 2,000. */
    public EerBand resolveUser(Long userId) {
        if (userId == null) return EerBand.DEFAULT;
        EerBand stored = bodyProfileService.storedBand(userId);
        return stored != null ? stored : EerBand.DEFAULT;
    }
}
