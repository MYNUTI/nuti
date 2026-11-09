package com.example.nutriuniv.domain.goal.service;

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
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
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
 * <p>ConsentService 가 건강정보 철회 시 clearSavedGoal() 을 부르므로 이 서비스는 ConsentService 에 의존하지 않는다
 * (순환 방지) — 건강정보 동의 여부는 ConsentRepository 로 직접 본다.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private final UserGoalRepository userGoalRepository;
    private final ConsentRepository consentRepository;
    private final OwnerResolver ownerResolver;
    private final GradeLookupService gradeLookupService;
    private final ProductRepository productRepository;

    // ── GET /me/goal ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoalResponse getGoal(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);          // 기기 ID 도 없으면 감량(DEFAULT) 으로 정상 응답
        Optional<UserGoal> saved = owner == null ? Optional.empty() : find(owner);
        GoalType goal = saved.map(UserGoal::getGoal).orElse(GoalType.DEFAULT);
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
        Owner owner = ownerResolver.resolve(actor);                 // 기기 ID 없음 → 400
        GoalType goal = GoalType.from(request.getGoal());          // 허용값 외 → 400

        GoalType before = find(owner).map(UserGoal::getGoal).orElse(GoalType.DEFAULT);
        UserGoal ug = changeGoal(owner, goal);

        GoalUpdateResponse.Recalculation recalculation =
                request.getRecalcProductId() == null ? null : recalculate(request.getRecalcProductId(), before, goal);

        return GoalUpdateResponse.builder()
                .goal(goal.name())
                .label(goal.label())
                .appliedAt(ug.getAppliedAt())
                .recalculation(recalculation)
                .build();
    }

    /**
     * 목표 저장 (설정 화면 PATCH /me/settings 도 이걸 쓴다 — 재계산 응답 없음).
     * v3(ONB_004·ONB_009): 목표를 고르는 데는 동의가 필요 없다. 동의는 목표를 계정에 저장할 때만이고, 그 구조는 등급 브랜치에서 바꾼다.
     */
    @Transactional
    public UserGoal changeGoal(Owner owner, GoalType goal) {
        UserGoal ug = find(owner).orElse(null);
        if (ug == null) {
            return userGoalRepository.save(UserGoal.create(owner, goal));
        }
        ug.change(goal);
        return ug;
    }

    /** 건강정보 동의 철회 시 서버에 저장한 목표를 지운다 (ConsentService 에서 호출) — 목표는 기기에 남는다(v3). */
    @Transactional
    public void clearSavedGoal(Owner owner) {
        find(owner).ifPresent(userGoalRepository::delete);
    }

    /** 다른 도메인(bootstrap)에서 쓰는 현재 목표. 미설정·기기 ID 없음은 감량(DEFAULT). */
    @Transactional(readOnly = true)
    public GoalType currentGoal(Owner owner) {
        return gradeLookupService.resolveGoalType(owner);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    private Optional<UserGoal> find(Owner owner) {
        return owner.isUser()
                ? userGoalRepository.findByUserId(owner.userId())
                : userGoalRepository.findByAnonymousId(owner.anonymousId());
    }

    /** 동의는 계정만 한다 — 기기 ID 소유자는 false. */
    private boolean isHealthConsented(Owner owner) {
        if (!owner.isUser()) {
            return false;
        }
        return consentRepository.findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(owner.userId(), ConsentType.HEALTH)
                .map(c -> c.getAction() == ConsentAction.AGREE).orElse(false);
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
        String afterGrade = gradeLookupService.lookupGrade(productId, after);
        if (afterGrade == null) {
            return null;
        }
        String beforeGrade = gradeLookupService.lookupGrade(productId, before);
        boolean changed = !Objects.equals(beforeGrade, afterGrade);
        String reason = changed
                ? String.format("%s 목표 기준으로 다시 계산해 %s에서 %s로 바뀌었어요.", after.label(), beforeGrade, afterGrade)
                : String.format("%s 목표 기준으로도 %s 그대로예요.", after.label(), afterGrade);
        return GoalUpdateResponse.Recalculation.builder()
                .productId(product.getId())
                .name(product.getName())
                .before(GoalUpdateResponse.Grade.builder().grade(beforeGrade).label(gradeLookupService.label(before, beforeGrade)).build())
                .after(GoalUpdateResponse.Grade.builder().grade(afterGrade).label(gradeLookupService.label(after, afterGrade)).build())
                .changed(changed)
                .reason(reason)
                .build();
    }
}
