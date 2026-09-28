package com.example.nutriuniv.domain.goal.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.entity.ConsentAction;
import com.example.nutriuniv.domain.consent.entity.ConsentType;
import com.example.nutriuniv.domain.consent.repository.ConsentRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.dto.GoalResponse;
import com.example.nutriuniv.domain.goal.dto.GoalUpdateRequest;
import com.example.nutriuniv.domain.goal.dto.GoalUpdateResponse;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.pns.service.GradeLabel;
import com.example.nutriuniv.domain.pns.service.PnsLookupService;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * 목표 조회·설정 (기능명세서 2.4).
 *
 * <p>ConsentService 가 건강정보 철회 시 resetToGeneral() 을 부르므로 이 서비스는 ConsentService 에 의존하지 않는다
 * (순환 방지) — 건강정보 동의 여부는 ConsentRepository 로 직접 본다.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private final UserGoalRepository userGoalRepository;
    private final ConsentRepository consentRepository;
    private final OwnerResolver ownerResolver;
    private final PnsLookupService pnsLookupService;
    private final ProductRepository productRepository;

    // ── GET /me/goal ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoalResponse getGoal(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);          // 동의 전이면 GENERAL 로 정상 응답
        Optional<UserGoal> saved = owner == null ? Optional.empty() : find(owner);
        GoalType goal = saved.map(UserGoal::getGoal).orElse(GoalType.GENERAL);
        return GoalResponse.builder()
                .goal(goal.name())
                .label(goal.label())
                .healthConsented(owner != null && isHealthConsented(owner))
                .appliedAt(saved.map(UserGoal::getAppliedAt).orElse(null))
                .build();
    }

    // ── PUT /me/goal ────────────────────────────────────────────────────────────────

    @Transactional
    public GoalUpdateResponse setGoal(Actor actor, GoalUpdateRequest request) {
        Owner owner = ownerResolver.resolve(actor);                 // 동의 전 → 403 CONSENT_REQUIRED
        GoalType goal = GoalType.from(request.getGoal());          // 허용값 외 → 400
        if (goal.requiresHealthConsent() && !isHealthConsented(owner)) {
            throw new CustomException(ErrorCode.HEALTH_CONSENT_REQUIRED);   // 일반 거부(403 FORBIDDEN)와 구분
        }

        UserGoal ug = find(owner).orElse(null);
        GoalType before = ug == null ? GoalType.GENERAL : ug.getGoal();
        if (ug == null) {
            ug = userGoalRepository.save(UserGoal.create(owner, goal));
        } else {
            ug.change(goal);
        }

        GoalUpdateResponse.Recalculation recalculation =
                request.getRecalcProductId() == null ? null : recalculate(request.getRecalcProductId(), before, goal);

        return GoalUpdateResponse.builder()
                .goal(goal.name())
                .label(goal.label())
                .appliedAt(ug.getAppliedAt())
                .recalculation(recalculation)
                .build();
    }

    /** 건강정보 동의 철회 시 감량·근육증가 목표를 GENERAL 로 되돌린다 (ConsentService 에서 호출). */
    @Transactional
    public void resetToGeneral(Owner owner) {
        find(owner).filter(g -> g.getGoal() != GoalType.GENERAL).ifPresent(g -> g.change(GoalType.GENERAL));
    }

    /** 다른 도메인(bootstrap·상품 결과 화면)에서 쓰는 현재 목표. 미설정·동의 전은 GENERAL. */
    @Transactional(readOnly = true)
    public GoalType currentGoal(Owner owner) {
        return pnsLookupService.resolveGoalType(owner);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    private Optional<UserGoal> find(Owner owner) {
        return owner.isUser()
                ? userGoalRepository.findByUserId(owner.userId())
                : userGoalRepository.findByAnonymousId(owner.anonymousId());
    }

    private boolean isHealthConsented(Owner owner) {
        var last = owner.isUser()
                ? consentRepository.findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(owner.userId(), ConsentType.HEALTH)
                : consentRepository.findTopByAnonymousIdAndConsentTypeOrderByCreatedAtDesc(owner.anonymousId(), ConsentType.HEALTH);
        return last.map(c -> c.getAction() == ConsentAction.AGREE).orElse(false);
    }

    /**
     * 목표 변경 전/후 등급. 제품이 없거나 비활성·등급 미계산(INSUFFICIENT)이면 null → 화면 스킵.
     * 과거 기록을 재계산·저장하지 않는다 — 이후 조회부터 새 목표가 적용될 뿐이다.
     * reason 문장은 등급 근거(5.2) 도입 시 감점 요인 기반으로 대체한다.
     */
    private GoalUpdateResponse.Recalculation recalculate(Long productId, GoalType before, GoalType after) {
        Product product = productRepository.findById(productId).filter(Product::isActive).orElse(null);
        if (product == null) {
            return null;
        }
        String afterGrade = pnsLookupService.lookupGrade(productId, after);
        if (afterGrade == null) {
            return null;
        }
        String beforeGrade = pnsLookupService.lookupGrade(productId, before);
        boolean changed = !Objects.equals(beforeGrade, afterGrade);
        String reason = changed
                ? String.format("%s 목표 기준으로 다시 계산해 %s에서 %s로 바뀌었어요.", after.label(), beforeGrade, afterGrade)
                : String.format("%s 목표 기준으로도 %s 그대로예요.", after.label(), afterGrade);
        return GoalUpdateResponse.Recalculation.builder()
                .productId(product.getId())
                .name(product.getName())
                .before(GoalUpdateResponse.Grade.builder().grade(beforeGrade).label(GradeLabel.of(beforeGrade)).build())
                .after(GoalUpdateResponse.Grade.builder().grade(afterGrade).label(GradeLabel.of(afterGrade)).build())
                .changed(changed)
                .reason(reason)
                .build();
    }
}
