package com.example.nutriuniv.domain.body.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.body.dto.BodyProfileRequest;
import com.example.nutriuniv.domain.body.dto.BodyProfileResponse;
import com.example.nutriuniv.domain.body.entity.BodyProfile;
import com.example.nutriuniv.domain.body.repository.BodyProfileRepository;
import com.example.nutriuniv.domain.consent.entity.ConsentItemCode;
import com.example.nutriuniv.domain.consent.service.ConsentReader;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.user.repository.UserNutritionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 몸 정보 (v3 ACCT_010 · CORE_008).
 * <ul>
 *   <li>로그인 + 「몸 정보 수집·이용」 동의한 계정만 저장 · 조회한다(아니면 401 · 403 BODY_CONSENT_REQUIRED)</li>
 *   <li>하루 필요 칼로리는 기기가 계산해 보낸다. 서버는 가장 가까운 칼로리 구간(1,500 · 2,000 · 2,500 · 3,000, 중간이면 위)만 정한다</li>
 *   <li>입력 범위: 출생 연도 1900 ~ 만 14세가 되는 해, 키 100~250cm, 체중 25~250kg</li>
 *   <li>동의한 계정은 등급 계산에 저장한 구간을 쓴다(KcalBandResolver). 지우면 기본 2,000</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BodyProfileService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MIN_BIRTH_YEAR = 1900;
    static final int MIN_AGE = 14;
    static final BigDecimal MIN_HEIGHT = BigDecimal.valueOf(100), MAX_HEIGHT = BigDecimal.valueOf(250);
    static final BigDecimal MIN_WEIGHT = BigDecimal.valueOf(25), MAX_WEIGHT = BigDecimal.valueOf(250);
    static final int MIN_KCAL = 500, MAX_KCAL = 6000;

    private final BodyProfileRepository repository;
    private final UserNutritionRepository legacyNutritionRepository;
    private final ConsentReader consentReader;

    @Transactional(readOnly = true)
    public BodyProfileResponse get(Actor actor) {
        Long userId = requireConsentedUser(actor);
        return BodyProfileResponse.of(true, repository.findByUserId(userId).orElse(null), EerBand.DEFAULT.kcal());
    }

    @Transactional
    public BodyProfileResponse save(Actor actor, BodyProfileRequest req) {
        Long userId = requireConsentedUser(actor);
        return BodyProfileResponse.of(true, saveFor(userId, req), EerBand.DEFAULT.kcal());
    }

    /** 내 몸에 맞추기 지우기 — 값만 지운다(동의 철회는 동의 관리에서). */
    @Transactional
    public BodyProfileResponse clear(Actor actor) {
        Long userId = requireConsentedUser(actor);
        deleteFor(userId);
        return BodyProfileResponse.of(true, null, EerBand.DEFAULT.kcal());
    }

    // ── 다른 서비스용 ──────────────────────────────────────────────────────────────

    /** 동의 확인은 호출부가 한다(가입 · 동의 다시 켜기). */
    @Transactional
    public BodyProfile saveFor(Long userId, BodyProfileRequest req) {
        Valid v = validate(req);
        BodyProfile p = repository.findByUserId(userId).orElseGet(() -> BodyProfile.create(userId));
        p.update(v.gender(), v.birthYear(), req.heightCm(), req.weightKg(), v.activity(), v.dailyKcal(),
                EerBand.nearest(v.dailyKcal()).kcal());
        return repository.save(p);
    }

    /** 동의 철회 · 계정 삭제 — 몸 정보와 옛 영양정보(user_nutrition)를 함께 지운다. */
    @Transactional
    public void deleteFor(Long userId) {
        repository.deleteByUserId(userId);
        legacyNutritionRepository.findByUserId(userId).ifPresent(legacyNutritionRepository::delete);
    }

    /** 동의한 계정에 저장된 구간. 없으면 null. */
    @Transactional(readOnly = true)
    public EerBand storedBand(Long userId) {
        if (userId == null || !consentReader.isAgreed(userId, ConsentItemCode.BODY_PROFILE)) return null;
        return repository.findByUserId(userId).map(p -> EerBand.ofOrDefault(p.getKcalBand())).orElse(null);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private Long requireConsentedUser(Actor actor) {
        if (!actor.isLoggedIn()) throw new CustomException(ErrorCode.UNAUTHORIZED);
        if (!consentReader.isAgreed(actor.userId(), ConsentItemCode.BODY_PROFILE)) {
            throw new CustomException(ErrorCode.BODY_CONSENT_REQUIRED);
        }
        return actor.userId();
    }

    private record Valid(BodyProfile.Gender gender, int birthYear, BodyProfile.ActivityLevel activity, int dailyKcal) {}

    static Valid validate(BodyProfileRequest req) {
        if (req == null) throw bad("몸 정보가 비어 있습니다.");
        BodyProfile.Gender gender = parse(BodyProfile.Gender.class, req.gender(), "gender는 MALE · FEMALE 중 하나여야 합니다.");
        BodyProfile.ActivityLevel activity = parse(BodyProfile.ActivityLevel.class, req.activityLevel(),
                "activityLevel은 SEDENTARY · LOW_ACTIVE · ACTIVE · VERY_ACTIVE 중 하나여야 합니다.");
        int maxBirthYear = LocalDate.now(KST).getYear() - MIN_AGE;
        if (req.birthYear() == null || req.birthYear() < MIN_BIRTH_YEAR || req.birthYear() > maxBirthYear) {
            throw bad("birthYear는 " + MIN_BIRTH_YEAR + "~" + maxBirthYear + " 사이여야 합니다.");
        }
        if (outside(req.heightCm(), MIN_HEIGHT, MAX_HEIGHT)) throw bad("heightCm은 100~250 사이여야 합니다.");
        if (outside(req.weightKg(), MIN_WEIGHT, MAX_WEIGHT)) throw bad("weightKg은 25~250 사이여야 합니다.");
        if (req.dailyKcal() == null || req.dailyKcal() < MIN_KCAL || req.dailyKcal() > MAX_KCAL) {
            throw bad("dailyKcal은 " + MIN_KCAL + "~" + MAX_KCAL + " 사이여야 합니다.");
        }
        return new Valid(gender, req.birthYear(), activity, req.dailyKcal());
    }

    private static boolean outside(BigDecimal v, BigDecimal min, BigDecimal max) {
        return v == null || v.compareTo(min) < 0 || v.compareTo(max) > 0;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String message) {
        if (raw == null || raw.isBlank()) throw bad(message);
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad(message);
        }
    }

    private static CustomException bad(String message) {
        return new CustomException(ErrorCode.VALIDATION_FAILED, message);
    }
}
