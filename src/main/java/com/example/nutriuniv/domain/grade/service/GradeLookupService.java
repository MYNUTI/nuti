package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.entity.ProductGrade;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 등급 조회 — 목표에 맞는 사전계산 슬롯을 꺼낸다 (구 PnsLookupService 대체).
 * 열량구간은 1·2차 2000 고정(일반은 슬롯 0)이므로 호출부는 목표만 넘긴다.
 * 사전계산이 없는 제품(영양정보 부족·배치 전)은 Optional.empty / null — 호출부가 INSUFFICIENT 로 표시한다.
 */
@Service
@RequiredArgsConstructor
public class GradeLookupService {

    private final UserGoalRepository userGoalRepository;
    private final ProductGradeRepository productGradeRepository;
    private final CalibrationService calibrationService;
    private final EntityManager em;

    public record GradeView(
            BigDecimal score,
            String grade,
            String label,
            BigDecimal percentile,
            BigDecimal topPercent,
            String topPenaltyNutrient,
            int eerBand,
            GoalType goal
    ) {}

    // ── 목표 ──────────────────────────────────────────────────────────────────

    /** 소유자(로그인 또는 익명) 기준 목표. null(동의 전)·미설정은 GENERAL. */
    @Transactional(readOnly = true)
    public GoalType resolveGoalType(Owner owner) {
        if (owner == null) return GoalType.GENERAL;
        var found = owner.isUser()
                ? userGoalRepository.findByUserId(owner.userId())
                : userGoalRepository.findByAnonymousId(owner.anonymousId());
        return found.map(UserGoal::getGoal).orElse(GoalType.GENERAL);
    }

    /** 로그인 유저 id 기준 목표 (기존 userId 시그니처 호출부용). 비로그인·미설정은 GENERAL. */
    @Transactional(readOnly = true)
    public GoalType resolveGoalType(Long userId) {
        if (userId == null) return GoalType.GENERAL;
        return userGoalRepository.findByUserId(userId).map(UserGoal::getGoal).orElse(GoalType.GENERAL);
    }

    // ── 등급 ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<GradeView> lookup(Long productId, GoalType goal) {
        int slot = EerBand.defaultSlot(goal);
        return productGradeRepository.findByProductIdAndGoalAndEerBand(productId, goal, slot)
                .map(g -> toView(g, goal));
    }

    /** 등급 문자만 (A~E). 사전계산이 없으면 null. */
    @Transactional(readOnly = true)
    public String lookupGrade(Long productId, GoalType goal) {
        return lookup(productId, goal).map(GradeView::grade).orElse(null);
    }

    /** 여러 제품의 등급 문자 일괄 조회 (N+1 방지). 사전계산이 없는 제품은 키가 없다. */
    @Transactional(readOnly = true)
    public Map<Long, String> lookupGrades(Collection<Long> productIds, GoalType goal) {
        if (productIds == null || productIds.isEmpty()) return Map.of();
        int slot = EerBand.defaultSlot(goal);
        Map<Long, String> result = new HashMap<>();
        for (ProductGrade g : productGradeRepository.findByProductIdInAndGoalAndEerBand(productIds, goal, slot)) {
            result.put(g.getProductId(), g.getGrade().name());
        }
        return result;
    }

    /** 등급 라벨 — 현행 기준의 grade_cutoffs.label. grade 가 null 이면 null. */
    public String label(GoalType goal, String grade) {
        return calibrationService.label(goal, grade);
    }

    /** 대분류 안 활성 상품 수 (상품 상세 pns.categoryTotal). */
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

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private GradeView toView(ProductGrade g, GoalType goal) {
        BigDecimal percentile = g.getPercentile();
        BigDecimal topPercent = percentile == null
                ? null
                : BigDecimal.valueOf(100).subtract(percentile).setScale(2, RoundingMode.HALF_UP);
        String grade = g.getGrade().name();
        return new GradeView(
                g.getScore(),
                grade,
                calibrationService.label(goal, g.getGrade()),
                percentile,
                topPercent,
                g.getTopPenaltyNutrient() == null ? null : g.getTopPenaltyNutrient().name(),
                EerBand.DEFAULT.kcal(),
                goal
        );
    }
}
