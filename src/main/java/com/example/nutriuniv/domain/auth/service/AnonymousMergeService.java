package com.example.nutriuniv.domain.auth.service;

import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
import com.example.nutriuniv.domain.goal.entity.UserGoal;
import com.example.nutriuniv.domain.goal.repository.UserGoalRepository;
import com.example.nutriuniv.domain.history.repository.ViewHistoryRepository;
import com.example.nutriuniv.domain.report.repository.ProductReportRepository;
import com.example.nutriuniv.domain.saved.entity.SavedProduct;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 로그인 시 기기 기록을 계정으로 옮긴다 (v3 ACCT_005). 로그인과 <b>한 트랜잭션</b>으로 돈다(AuthService.login 이 부른다).
 * <ul>
 *   <li>기기 ID 는 기기가 만든 값이라 서버에 등록돼 있지 않아도 그 ID 소유의 기록을 그대로 옮긴다</li>
 *   <li>저장: 계정에 같은 제품이 있으면 건너뛰고(기기 행 삭제) 없으면 계정으로 이관</li>
 *   <li>목표: 「목표 계정 저장」에 동의한 계정이고 계정에 목표가 없을 때만 이관. 동의가 없으면 서버의 기기 목표 행을 지운다(목표는 기기에만)</li>
 *   <li>분석 요청 · 제보 · 최근 본 제품: 기록 전체를 계정으로</li>
 *   <li>옮긴 뒤 기기·계정 연결을 anonymous_users 에 남긴다(같은 기기면 마지막 계정으로 덮음)</li>
 * </ul>
 * 이미 옮긴 기기 ID 로 다시 로그인하면, 그 사이 새로 쌓인 기록만 남아 있으므로 그것만 옮겨진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousMergeService {

    private final AnonymousUserRepository anonymousUserRepository;
    private final SavedProductRepository savedProductRepository;
    private final UserGoalRepository userGoalRepository;
    private final AnalysisRequestEventRepository analysisRequestEventRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ProductReportRepository productReportRepository;

    public record MergeResult(int savedProducts, int goals, int contributions, int history, int reports) {
        public static final MergeResult NONE = new MergeResult(0, 0, 0, 0, 0);

        /** mergedRecordCount — 옮긴 기록 합계. */
        public int total() {
            return savedProducts + goals + contributions + history + reports;
        }
    }

    /** anonymousId 는 ActorArgumentResolver 가 UUID 모양만 통과시킨 값(없으면 null). */
    /**
     * 로그인 트랜잭션과 따로 커밋한다 — 옮기다 실패해도 로그인은 끝나고 다음 로그인 때 다시 옮긴다(ACCT_005).
     * goalConsented: 「목표 계정 저장」 동의 여부(가입 중에는 동의 기록이 아직 커밋 전이라 호출부가 넘긴다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MergeResult merge(String anonymousId, User user, boolean goalConsented) {
        if (anonymousId == null || anonymousId.isBlank()) {
            return MergeResult.NONE;
        }
        Long userId = user.getId();

        // 1) 저장 — 계정에 같은 제품이 있으면 건너뜀
        int saved = 0;
        for (SavedProduct s : savedProductRepository.findByAnonymousId(anonymousId)) {
            Long productId = s.getProduct().getId();
            if (savedProductRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
                savedProductRepository.delete(s);
            } else {
                s.transferTo(userId);
                saved++;
            }
        }

        // 2) 목표 — 「목표 계정 저장」 동의 + 계정에 목표가 없을 때만
        int goals = 0;
        Optional<UserGoal> anonGoal = userGoalRepository.findByAnonymousId(anonymousId);
        if (anonGoal.isPresent()) {
            if (!goalConsented || userGoalRepository.findByUserId(userId).isPresent()) {
                userGoalRepository.delete(anonGoal.get());
            } else {
                anonGoal.get().transferTo(userId);
                goals = 1;
            }
        }

        // 3) 기여 — 분석 대기 접수 기록
        int contributions = analysisRequestEventRepository.transferToUser(anonymousId, userId);

        // 4) 최근 본 제품 · 제보
        int history = viewHistoryRepository.transferToUser(anonymousId, userId);
        int reports = productReportRepository.transferToUser(anonymousId, userId);

        // 5) 기기·계정 연결 기록
        anonymousUserRepository.findByAnonymousId(anonymousId).ifPresentOrElse(
                a -> a.markMerged(userId),
                () -> anonymousUserRepository.save(AnonymousUser.linked(anonymousId, userId)));

        MergeResult result = new MergeResult(saved, goals, contributions, history, reports);
        log.info("[MERGE] anonymousId={} → userId={} 저장 {} 목표 {} 요청 {} 기록 {} 제보 {}", anonymousId, userId, saved, goals, contributions, history, reports);
        return result;
    }
}
