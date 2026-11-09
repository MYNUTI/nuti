package com.example.nutriuniv.domain.auth.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.saved.entity.SavedProduct;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** v3 ACCT_005 — 기기 기록을 계정으로: 같은 제품 건너뜀 · 계정 목표 우선 · 등록 안 된 기기 ID 도 옮김 · 연결 기록 · 건수 반환. */
@ExtendWith(MockitoExtension.class)
class AnonymousMergeServiceTest {

    private static final String DEVICE = "3f2b8c1e-9d4a-4b6f-8e2a-1c5d7f9a0b3e";

    @Mock AnonymousUserRepository anonymousUserRepository;
    @Mock SavedProductRepository savedProductRepository;
    @Mock UserGoalRepository userGoalRepository;
    @Mock AnalysisRequestEventRepository analysisRequestEventRepository;

    @InjectMocks AnonymousMergeService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.register("kakao", "oauth-1", "u@example.com");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void 헤더가_비어_있으면_0건() {
        assertEquals(0, service.merge(null, user).total());
        assertEquals(0, service.merge("  ", user).total());
        verifyNoInteractions(anonymousUserRepository, savedProductRepository, userGoalRepository, analysisRequestEventRepository);
    }

    @Test
    void 처음_로그인한_기기는_기록을_옮기고_연결을_새로_남긴다() {
        Owner device = Owner.ofAnonymous(DEVICE);

        // 저장: 제품 101 은 계정에 이미 있음(건너뜀), 102 는 이관
        Product p101 = product(101L);
        Product p102 = product(102L);
        SavedProduct dup = SavedProduct.create(device, p101);
        SavedProduct mv  = SavedProduct.create(device, p102);
        when(savedProductRepository.findByAnonymousId(DEVICE)).thenReturn(List.of(dup, mv));
        when(savedProductRepository.findByUserIdAndProductId(7L, 101L)).thenReturn(Optional.of(SavedProduct.create(Owner.ofUser(7L), p101)));
        when(savedProductRepository.findByUserIdAndProductId(7L, 102L)).thenReturn(Optional.empty());

        // 목표: 계정에 없음 → 이관 (동의 없이 고른 목표도 옮긴다)
        UserGoal deviceGoal = UserGoal.create(device, GoalType.WEIGHT_LOSS);
        when(userGoalRepository.findByAnonymousId(DEVICE)).thenReturn(Optional.of(deviceGoal));
        when(userGoalRepository.findByUserId(7L)).thenReturn(Optional.empty());

        when(analysisRequestEventRepository.transferToUser(DEVICE, 7L)).thenReturn(3);
        when(anonymousUserRepository.findByAnonymousId(DEVICE)).thenReturn(Optional.empty());   // 서버에 등록된 적 없는 기기

        AnonymousMergeService.MergeResult r = service.merge(DEVICE, user);

        assertEquals(1, r.savedProducts());
        assertEquals(1, r.goals());
        assertEquals(3, r.contributions());
        assertEquals(5, r.total());

        verify(savedProductRepository).delete(dup);                    // 같은 제품 → 기기 행 삭제
        assertEquals(7L, mv.getUserId());
        assertNull(mv.getAnonymousId());
        assertEquals(7L, deviceGoal.getUserId());

        ArgumentCaptor<AnonymousUser> link = ArgumentCaptor.forClass(AnonymousUser.class);
        verify(anonymousUserRepository).save(link.capture());
        assertEquals(DEVICE, link.getValue().getAnonymousId());
        assertEquals(7L, link.getValue().getMergedUserId());
    }

    @Test
    void 이미_옮긴_기기는_남은_기록만_옮기고_연결을_마지막_계정으로_덮는다() {
        AnonymousUser link = AnonymousUser.linked(DEVICE, 99L);
        when(anonymousUserRepository.findByAnonymousId(DEVICE)).thenReturn(Optional.of(link));
        when(savedProductRepository.findByAnonymousId(DEVICE)).thenReturn(List.of());
        when(userGoalRepository.findByAnonymousId(DEVICE)).thenReturn(Optional.empty());
        when(analysisRequestEventRepository.transferToUser(DEVICE, 7L)).thenReturn(1);   // 연결이 끊긴 뒤 새로 쌓인 1건

        AnonymousMergeService.MergeResult r = service.merge(DEVICE, user);

        assertEquals(1, r.total());
        assertEquals(7L, link.getMergedUserId());
        verify(anonymousUserRepository, never()).save(any());
    }

    @Test
    void 계정에_목표가_있으면_기기_목표는_버린다() {
        when(savedProductRepository.findByAnonymousId(DEVICE)).thenReturn(List.of());
        UserGoal deviceGoal = UserGoal.create(Owner.ofAnonymous(DEVICE), GoalType.MUSCLE_GAIN);
        when(userGoalRepository.findByAnonymousId(DEVICE)).thenReturn(Optional.of(deviceGoal));
        when(userGoalRepository.findByUserId(7L)).thenReturn(Optional.of(UserGoal.create(Owner.ofUser(7L), GoalType.GENERAL)));
        when(analysisRequestEventRepository.transferToUser(DEVICE, 7L)).thenReturn(0);
        when(anonymousUserRepository.findByAnonymousId(DEVICE)).thenReturn(Optional.empty());

        AnonymousMergeService.MergeResult r = service.merge(DEVICE, user);

        assertEquals(0, r.goals());
        verify(userGoalRepository).delete(deviceGoal);
        assertNull(deviceGoal.getUserId());
    }

    private static Product product(long id) {
        Product p = Product.create("제품" + id, null, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
