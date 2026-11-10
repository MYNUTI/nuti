package com.example.nutriuniv.domain.body.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 등급 계산에 쓸 칼로리 구간 (v3 CORE_008).
 * 몸 정보에 동의하고 값을 저장한 계정이면 저장된 구간, 아니면 요청 값(없거나 4종 밖이면 2,000).
 */
@Service
@RequiredArgsConstructor
public class KcalBandResolver {

    private final BodyProfileService bodyProfileService;

    public EerBand resolve(Actor actor, Integer requested) {
        if (actor != null && actor.isLoggedIn()) {
            EerBand stored = bodyProfileService.storedBand(actor.userId());
            if (stored != null) return stored;
        }
        return EerBand.ofOrDefault(requested);
    }
}
