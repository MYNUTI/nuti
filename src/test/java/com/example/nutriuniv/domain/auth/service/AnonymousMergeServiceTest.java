package com.example.nutriuniv.domain.auth.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.entity.Consent;
import com.example.nutriuniv.domain.consent.entity.ConsentType;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import com.example.nutriuniv.domain.consent.repository.ConsentRepository;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.me.repository.UserSettingRepository;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.saved.entity.SavedProduct;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 기능명세서 7.2 — 병합 규칙: 같은 제품 건너뜀 · 계정 목표 우선 · 이미 병합된 ID 무시 · 건수 반환. */
@ExtendWith(MockitoExtension.class)
class AnonymousMergeServiceTest {

    @Mock AnonymousUserRepository anonymousUserRepository;
    @Mock SavedProductRepository savedProductRepository;
    @Mock UserGoalRepository userGoalRepository;
    @Mock ConsentRepository consentRepository;
    @Mock AnalysisRequestEventRepository analysisRequestEventRepository;
    @Mock UserSettingRepository userSettingRepository;

    @InjectMocks AnonymousMergeService service;

    private User user;
    private AnonymousUser anon;

    @BeforeEach
    void setUp() {
        user = User.register("kakao", "oauth-1", "u@example.com");
        ReflectionTestUtils.setField(user, "id", 7L);
        anon = AnonymousUser.issue();
    }

    @Test
    void 미발급_ID는_무시하고_0건() {
        when(anonymousUserRepository.findByAnonymousId("ghost")).thenReturn(Optional.empty());

        AnonymousMergeService.MergeResult r = service.merge("ghost", user);

        assertEquals(0, r.total());
        verifyNoInteractions(savedProductRepository, userGoalRepository, analysisRequestEventRepository);
    }

    @Test
    void 이미_병합된_ID는_무시() {
        anon.markMerged(99L);
        when(anonymousUserRepository.findByAnonymousId(anon.getAnonymousId())).thenReturn(Optional.of(anon));

        assertEquals(AnonymousMergeService.MergeResult.NONE, service.merge(anon.getAnonymousId(), user));
        assertEquals(99L, anon.getMergedUserId());          // 원래 병합 대상 유지
        verifyNoInteractions(savedProductRepository);
    }

    @Test
    void 헤더가_비어_있으면_0건() {
        assertEquals(0, service.merge(null, user).total());
        assertEquals(0, service.merge("  ", user).total());
        verifyNoInteractions(anonymousUserRepository);
    }

    @Test
    void 저장_목표_기여_동의를_계정으로_옮기고_같은_제품은_건너뛴다() {
        String anonId = anon.getAnonymousId();
        Owner anonOwner = Owner.ofAnonymous(anonId);
        anon.completeOnboarding();
        when(anonymousUserRepository.findByAnonymousId(anonId)).thenReturn(Optional.of(anon));

        // 동의: 익명은 개인정보·건강정보 모두 AGREE, 계정은 아직 없음
        Consent privacy = Consent.agree(anonOwner, ConsentType.PRIVACY, "v1", List.of("PERSONAL_INFO", "AGE_OVER_14"), "hash", "ua");
        Consent health  = Consent.agree(anonOwner, ConsentType.HEALTH,  "v1", List.of("HEALTH_GOAL"), "hash", "ua");
        when(consentRepository.findTopByAnonymousIdAndConsentTypeOrderByCreatedAtDesc(anonId, ConsentType.PRIVACY)).thenReturn(Optional.of(privacy));
        when(consentRepository.findTopByAnonymousIdAndConsentTypeOrderByCreatedAtDesc(anonId, ConsentType.HEALTH)).thenReturn(Optional.of(health));
        when(consentRepository.findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(eq(7L), any())).thenReturn(Optional.empty());

        // 저장: 제품 101 은 계정에 이미 있음(건너뜀), 102 는 이관
        Product p101 = product(101L);
        Product p102 = product(102L);
        SavedProduct dup = SavedProduct.create(anonOwner, p101);
        SavedProduct mv  = SavedProduct.create(anonOwner, p102);
        when(savedProductRepository.findByAnonymousId(anonId)).thenReturn(List.of(dup, mv));
        when(savedProductRepository.findByUserIdAndProductId(7L, 101L)).thenReturn(Optional.of(SavedProduct.create(Owner.ofUser(7L), p101)));
        when(savedProductRepository.findByUserIdAndProductId(7L, 102L)).thenReturn(Optional.empty());

        // 목표: 계정에 없음 → 이관
        UserGoal anonGoal = UserGoal.create(anonOwner, GoalType.WEIGHT_LOSS);
        when(userGoalRepository.findByAnonymousId(anonId)).thenReturn(Optional.of(anonGoal));
        when(userGoalRepository.findByUserId(7L)).thenReturn(Optional.empty());

        // 기여 3건
        when(analysisRequestEventRepository.transferToUser(anonId, 7L)).thenReturn(3);

        AnonymousMergeService.MergeResult r = service.merge(anonId, user);

        assertEquals(1, r.savedProducts());
        assertEquals(1, r.goals());
        assertEquals(3, r.contributions());
        assertEquals(2, r.consents());
        assertEquals(5, r.total());                                   // 저장 1 + 목표 1 + 기여 3 (동의는 제외)

        verify(savedProductRepository).delete(dup);                    // 같은 제품 → 익명 행 삭제
        assertEquals(7L, mv.getUserId());
        assertNull(mv.getAnonymousId());
        assertEquals(7L, anonGoal.getUserId());
        verify(consentRepository, times(2)).save(any(Consent.class)); // 개인정보·건강정보 각 1행 덧붙임
        assertTrue(user.isPersonalInfoAgreed());
        assertTrue(user.isHealthInfoAgreed());
        assertNotNull(user.getOnboardingCompletedAt());
        assertTrue(anon.isMerged());
        assertEquals(7L, anon.getMergedUserId());
    }

    @Test
    void 계정에_목표가_있으면_익명_목표는_버린다() {
        String anonId = anon.getAnonymousId();
        when(anonymousUserRepository.findByAnonymousId(anonId)).thenReturn(Optional.of(anon));
        when(consentRepository.findTopByAnonymousIdAndConsentTypeOrderByCreatedAtDesc(anyString(), any())).thenReturn(Optional.empty());
        when(savedProductRepository.findByAnonymousId(anonId)).thenReturn(List.of());
        UserGoal anonGoal = UserGoal.create(Owner.ofAnonymous(anonId), GoalType.MUSCLE_GAIN);
        when(userGoalRepository.findByAnonymousId(anonId)).thenReturn(Optional.of(anonGoal));
        when(userGoalRepository.findByUserId(7L)).thenReturn(Optional.of(UserGoal.create(Owner.ofUser(7L), GoalType.GENERAL)));
        when(analysisRequestEventRepository.transferToUser(anonId, 7L)).thenReturn(0);

        AnonymousMergeService.MergeResult r = service.merge(anonId, user);

        assertEquals(0, r.goals());
        verify(userGoalRepository).delete(anonGoal);
        assertNull(anonGoal.getUserId());
        assertTrue(anon.isMerged());
    }

    private static Product product(long id) {
        Product p = Product.create("제품" + id, null, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
