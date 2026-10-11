package com.example.nutriuniv.domain.me.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestStatus;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.entity.Policy;
import com.example.nutriuniv.domain.consent.entity.PolicyType;
import com.example.nutriuniv.domain.consent.repository.PolicyRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.service.GoalService;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.me.dto.MeSettingsResponse;
import com.example.nutriuniv.domain.me.dto.MeSettingsUpdateRequest;
import com.example.nutriuniv.domain.me.dto.MeSummaryResponse;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이·설정 화면 (API 명세 /me/summary · /me/settings). 로그인·비로그인이 같은 API 를 쓰고 소유자만 다르다.
 */
@Service
public class MeService {

    private static final String LOGIN_NUDGE = "로그인하면 기기가 바뀌어도 기록이 유지돼요";

    private final OwnerResolver ownerResolver;
    private final UserRepository userRepository;
    private final GradeLookupService gradeLookupService;
    private final GoalService goalService;
    private final SavedProductRepository savedProductRepository;
    private final AnalysisRequestEventRepository analysisRequestEventRepository;
    private final UserSettingService userSettingService;
    private final PolicyRepository policyRepository;
    private final String supportUrl;
    private final String appVersion;

    public MeService(OwnerResolver ownerResolver,
                     UserRepository userRepository,
                     GradeLookupService gradeLookupService,
                     GoalService goalService,
                     SavedProductRepository savedProductRepository,
                     AnalysisRequestEventRepository analysisRequestEventRepository,
                     UserSettingService userSettingService,
                     PolicyRepository policyRepository,
                     @Value("${app.support-url:}") String supportUrl,
                     @Value("${app.app-version:2.0.0-web}") String appVersion) {
        this.ownerResolver = ownerResolver;
        this.userRepository = userRepository;
        this.gradeLookupService = gradeLookupService;
        this.goalService = goalService;
        this.savedProductRepository = savedProductRepository;
        this.analysisRequestEventRepository = analysisRequestEventRepository;
        this.userSettingService = userSettingService;
        this.policyRepository = policyRepository;
        this.supportUrl = supportUrl == null || supportUrl.isBlank() ? null : supportUrl;
        this.appVersion = appVersion;
    }

    // ── GET /me/summary ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MeSummaryResponse summary(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        GoalType goal = gradeLookupService.resolveGoalType(owner);

        MeSummaryResponse.Profile profile = null;
        if (owner != null && owner.isUser()) {
            User user = userRepository.findById(owner.userId()).orElse(null);
            if (user != null) {
                profile = MeSummaryResponse.Profile.builder()
                        .nickname(user.getNickname())
                        .email(user.getEmail())
                        .provider(user.getOauthProvider() == null ? null : user.getOauthProvider().toUpperCase())
                        .build();
            }
        }

        long recordCount = 0, requestCount = 0, publishedCount = 0;
        if (owner != null) {
            if (owner.isUser()) {
                recordCount    = savedProductRepository.countByUserId(owner.userId());
                requestCount   = analysisRequestEventRepository.countByUserId(owner.userId());
                publishedCount = analysisRequestEventRepository.countDistinctRequestsByUserIdAndStatus(owner.userId(), AnalysisRequestStatus.DONE);
            } else {
                recordCount    = savedProductRepository.countByAnonymousId(owner.anonymousId());
                requestCount   = analysisRequestEventRepository.countByAnonymousId(owner.anonymousId());
                publishedCount = analysisRequestEventRepository.countDistinctRequestsByAnonymousIdAndStatus(owner.anonymousId(), AnalysisRequestStatus.DONE);
            }
        }

        return MeSummaryResponse.builder()
                .loggedIn(actor.isLoggedIn())
                .profile(profile)
                .goal(MeSummaryResponse.Goal.builder().code(goal.name()).label(goal.label()).build())
                .recordCount(recordCount)
                .contribution(MeSummaryResponse.Contribution.builder().requestCount(requestCount).publishedCount(publishedCount).build())
                .footerNotice(actor.isLoggedIn() ? null : LOGIN_NUDGE)
                .build();
    }

    // ── GET /me/settings ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MeSettingsResponse settings(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        return buildSettings(owner);
    }

    // ── PATCH /me/settings ──────────────────────────────────────────────────────────

    @Transactional
    public MeSettingsResponse update(Actor actor, MeSettingsUpdateRequest request) {
        Owner owner = ownerResolver.resolve(actor);                      // 동의 전 → 403 CONSENT_REQUIRED
        boolean hasGoal = request.getGoal() != null && !request.getGoal().isBlank();
        if (request.getClipboardLinkDetection() == null && !hasGoal) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "수정할 항목이 없습니다.");
        }
        if (hasGoal) {
            goalService.changeGoal(owner, GoalType.from(request.getGoal()));   // 허용값 외 400, 건강 동의 없으면 403
        }
        if (request.getClipboardLinkDetection() != null) {
            userSettingService.changeClipboardLinkDetection(owner, request.getClipboardLinkDetection());
        }
        return buildSettings(owner);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    private MeSettingsResponse buildSettings(Owner owner) {
        GoalType goal = gradeLookupService.resolveGoalType(owner);
        return MeSettingsResponse.builder()
                .clipboardLinkDetection(userSettingService.clipboardLinkDetection(owner))
                .goal(MeSettingsResponse.Goal.builder().code(goal.name()).label(goal.label()).build())
                .policies(MeSettingsResponse.Policies.builder()
                        .privacyUrl(policyUrl(PolicyType.PRIVACY))
                        .termsUrl(policyUrl(PolicyType.TERMS))
                        .build())
                .supportUrl(supportUrl)
                .appVersion(appVersion)
                .build();
    }

    private String policyUrl(PolicyType type) {
        return policyRepository.findByPolicyTypeAndIsCurrentTrue(type).map(Policy::getUrl).orElse(null);
    }
}
