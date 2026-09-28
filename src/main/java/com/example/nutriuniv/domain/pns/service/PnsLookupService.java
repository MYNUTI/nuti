package com.example.nutriuniv.domain.pns.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.pns.entity.ProductPnsByEer;
import com.example.nutriuniv.domain.pns.repository.ProductPnsByEerRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 등급 조회 — 목표·열량구간에 맞는 사전계산 값을 꺼낸다.
 *
 * <p>2차 명세: 목표는 user_goals(사용자가 고른 GENERAL/WEIGHT_LOSS/MUSCLE_GAIN)에서 읽고, 열량구간은 2000 고정.
 * 이전에는 UserNutrition(키·몸무게·활동량)에서 EER 과 diet_purpose 를 유도했으나 2차는 그 정보를 받지 않는다
 * (개인화 4.3 은 3차 — UserNutrition·EerCalculator 는 보존).
 */
@Service
@RequiredArgsConstructor
public class PnsLookupService {

    public static final int    DEFAULT_EER_BAND = 2000;
    public static final String DEFAULT_GOAL     = GoalType.GENERAL.pnsGoal(); // 비로그인/미설정 기본값: health

    private final UserGoalRepository        userGoalRepository;
    private final ProductPnsByEerRepository pnsRepository;
    private final EntityManager             em;

    /** 1·2차: 열량구간 2000 고정 (기능명세서 4.1 — 구간별 사전계산은 하되 조회는 2000 만 쓴다). */
    public int resolveEerBand(Long userId) {
        return DEFAULT_EER_BAND;
    }

    /** 로그인 유저의 목표 → pns goal. 미설정·비로그인은 health(GENERAL). */
    @Transactional(readOnly = true)
    public String resolveGoal(Long userId) {
        if (userId == null) return DEFAULT_GOAL;
        return userGoalRepository.findByUserId(userId)
                .map(UserGoal::getGoal)
                .map(GoalType::pnsGoal)
                .orElse(DEFAULT_GOAL);
    }

    /** 소유자(로그인 또는 익명) 기준 목표. null(동의 전)이면 GENERAL. */
    @Transactional(readOnly = true)
    public GoalType resolveGoalType(Owner owner) {
        if (owner == null) return GoalType.GENERAL;
        var found = owner.isUser()
                ? userGoalRepository.findByUserId(owner.userId())
                : userGoalRepository.findByAnonymousId(owner.anonymousId());
        return found.map(UserGoal::getGoal).orElse(GoalType.GENERAL);
    }

    @Transactional(readOnly = true)
    public PnsLookupResult lookup(Long productId, int eerBand, String goal) {
        // health goal은 항상 HEALTH_BAND(2000) 사용
        int lookupBand = "health".equals(goal) ? 2000 : eerBand;
        ProductPnsByEer pns = pnsRepository.findOneByProductIdAndEerBandAndGoal(productId, lookupBand, goal);
        if (pns == null) return null;

        BigDecimal percentile = pns.getPercentile();
        BigDecimal topPercent = percentile == null
                ? null
                : BigDecimal.valueOf(100).subtract(percentile)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return new PnsLookupResult(
                pns.getScore(),
                pns.getGrade(),
                pns.getHealthScore(),
                percentile,
                topPercent,
                lookupBand,
                goal
        );
    }

    /** 목표별 등급 문자만 (A~E). 사전계산이 없으면 null. */
    @Transactional(readOnly = true)
    public String lookupGrade(Long productId, GoalType goal) {
        PnsLookupResult r = lookup(productId, DEFAULT_EER_BAND, goal.pnsGoal());
        return r == null ? null : r.grade();
    }

    @Transactional(readOnly = true)
    public Map<Long, String> lookupGrades(List<Long> productIds, int eerBand, String goal) {
        // health goal은 항상 HEALTH_BAND(2000) 사용
        int lookupBand = "health".equals(goal) ? 2000 : eerBand;
        return pnsRepository.findGradesByProductIdsAndEerBandAndGoal(productIds, lookupBand, goal);
    }

    @Transactional(readOnly = true)
    public int countActiveByParentCategory(Long parentCategoryId) {
        if (parentCategoryId == null) return 0;

        String sql = """
                SELECT COUNT(*)
                FROM   products p
                JOIN   categories c ON p.category_id = c.id
                WHERE  c.parent_id = ?1
                  AND  p.is_active = TRUE
                """;
        Number cnt = (Number) em.createNativeQuery(sql)
                .setParameter(1, parentCategoryId)
                .getSingleResult();
        return cnt == null ? 0 : cnt.intValue();
    }

    public record PnsLookupResult(
            BigDecimal score,
            String     grade,
            BigDecimal healthScore,
            BigDecimal percentile,
            BigDecimal topPercent,
            int        eerBand,
            String     goal
    ) {}
}
