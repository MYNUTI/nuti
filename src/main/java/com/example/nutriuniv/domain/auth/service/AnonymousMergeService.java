package com.example.nutriuniv.domain.auth.service;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.entity.Consent;
import com.example.nutriuniv.domain.consent.entity.ConsentAction;
import com.example.nutriuniv.domain.consent.entity.ConsentItem;
import com.example.nutriuniv.domain.consent.entity.ConsentType;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import com.example.nutriuniv.domain.consent.repository.ConsentRepository;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.me.entity.UserSetting;
import com.example.nutriuniv.domain.me.repository.UserSettingRepository;
import com.example.nutriuniv.domain.saved.entity.SavedProduct;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 로그인 시 익명 기록 병합 (기능명세서 7.2). 로그인과 <b>한 트랜잭션</b>으로 돈다(AuthService.login 이 부른다).
 * <ul>
 *   <li>서버가 발급하지 않은 ID·이미 병합된 ID 는 무시하고 로그인만 진행 (0건)</li>
 *   <li>저장: 계정에 같은 제품이 있으면 건너뛰고(익명 행 삭제) 없으면 계정으로 이관</li>
 *   <li>목표: 계정에 목표가 없을 때만 이관. 감량·근육 목표의 전제인 건강정보 동의를 먼저 승계한다</li>
 *   <li>기여: 분석 대기 접수 기록의 소유자를 계정으로</li>
 *   <li>동의(개인정보·건강정보)와 온보딩 완료는 상태 승계 — 병합 건수(저장·목표·기여)에는 넣지 않는다</li>
 * </ul>
 * 병합된 익명 ID 는 그 뒤로 소유자로 인정되지 않는다(OwnerResolver). 로그아웃은 새 ID 를 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousMergeService {

    private final AnonymousUserRepository anonymousUserRepository;
    private final SavedProductRepository savedProductRepository;
    private final UserGoalRepository userGoalRepository;
    private final ConsentRepository consentRepository;
    private final AnalysisRequestEventRepository analysisRequestEventRepository;
    private final UserSettingRepository userSettingRepository;

    public record MergeResult(int savedProducts, int goals, int contributions, int consents) {
        public static final MergeResult NONE = new MergeResult(0, 0, 0, 0);

        /** 명세의 mergedRecordCount — 저장·목표·기여. */
        public int total() {
            return savedProducts + goals + contributions;
        }
    }

    @Transactional
    public MergeResult merge(String anonymousId, User user) {
        if (anonymousId == null || anonymousId.isBlank()) {
            return MergeResult.NONE;
        }
        AnonymousUser anon = anonymousUserRepository.findByAnonymousId(anonymousId).orElse(null);
        if (anon == null || anon.isMerged()) {
            log.info("[MERGE] 병합 대상 아님(미발급 또는 이미 병합) — anonymousId={}", anonymousId);
            return MergeResult.NONE;
        }
        Long userId = user.getId();
        String anonId = anon.getAnonymousId();

        // 1) 동의 승계 — 목표 이관의 전제(감량·근육 증가는 건강정보 동의 필요)
        int consents = carryConsent(anon, user, ConsentType.PRIVACY) + carryConsent(anon, user, ConsentType.HEALTH);

        // 2) 저장 — 계정에 같은 제품이 있으면 건너뜀
        int saved = 0;
        for (SavedProduct s : savedProductRepository.findByAnonymousId(anonId)) {
            Long productId = s.getProduct().getId();
            if (savedProductRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
                savedProductRepository.delete(s);
            } else {
                s.transferTo(userId);
                saved++;
            }
        }

        // 3) 목표 — 계정에 목표가 없을 때만
        int goals = 0;
        Optional<UserGoal> anonGoal = userGoalRepository.findByAnonymousId(anonId);
        if (anonGoal.isPresent()) {
            if (userGoalRepository.findByUserId(userId).isPresent()) {
                userGoalRepository.delete(anonGoal.get());
            } else {
                anonGoal.get().transferTo(userId);
                goals = 1;
            }
        }

        // 4) 기여 — 분석 대기 접수 기록
        int contributions = analysisRequestEventRepository.transferToUser(anonId, userId);

        // 5) 온보딩 완료 상태
        if (anon.getOnboardingCompletedAt() != null) {
            user.completeOnboarding();
        }

        // 6) 설정 — 계정에 설정이 없을 때만 이관 (건수 제외)
        Optional<UserSetting> anonSetting = userSettingRepository.findByAnonymousId(anonId);
        if (anonSetting.isPresent()) {
            if (userSettingRepository.findByUserId(userId).isPresent()) {
                userSettingRepository.delete(anonSetting.get());
            } else {
                anonSetting.get().transferTo(userId);
            }
        }

        anon.markMerged(userId);
        MergeResult result = new MergeResult(saved, goals, contributions, consents);
        log.info("[MERGE] anonymousId={} → userId={} 저장 {} 목표 {} 기여 {} 동의 {}", anonId, userId, saved, goals, contributions, consents);
        return result;
    }

    /**
     * 익명의 최신 상태가 AGREE 이고 계정은 아직 AGREE 가 아니면, 그 동의 행(버전·항목·증빙)을 계정 소유로 한 줄 덧붙인다(append-only).
     * @return 덧붙였으면 1
     */
    private int carryConsent(AnonymousUser anon, User user, ConsentType type) {
        Optional<Consent> anonLatest = consentRepository
                .findTopByAnonymousIdAndConsentTypeOrderByCreatedAtDesc(anon.getAnonymousId(), type);
        if (anonLatest.isEmpty() || anonLatest.get().getAction() != ConsentAction.AGREE) {
            return 0;
        }
        Optional<Consent> userLatest = consentRepository
                .findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(user.getId(), type);
        if (userLatest.isPresent() && userLatest.get().getAction() == ConsentAction.AGREE) {
            return 0;
        }
        Consent src = anonLatest.get();
        List<String> items = src.getItems().stream().map(ConsentItem::getItemCode).toList();
        consentRepository.save(Consent.agree(Owner.ofUser(user.getId()), type, src.getPolicyVersion(), items,
                src.getIpHash(), src.getUserAgent()));
        if (type == ConsentType.PRIVACY) {
            user.agreePersonalInfo();
        } else {
            user.agreeHealthInfo();
        }
        return 1;
    }
}
