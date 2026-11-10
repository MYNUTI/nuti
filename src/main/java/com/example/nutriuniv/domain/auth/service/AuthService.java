package com.example.nutriuniv.domain.auth.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.JwtService;
import com.example.nutriuniv.domain.auth.client.OAuthClient;
import com.example.nutriuniv.domain.auth.client.OAuthClientFactory;
import com.example.nutriuniv.domain.auth.client.OAuthUserInfo;
import com.example.nutriuniv.domain.auth.dto.OAuthLoginRequest;
import com.example.nutriuniv.domain.auth.dto.RefreshRequest;
import com.example.nutriuniv.domain.auth.dto.SignupRequest;
import com.example.nutriuniv.domain.auth.dto.TokenResponse;
import com.example.nutriuniv.domain.auth.entity.AuthToken;
import com.example.nutriuniv.domain.auth.repository.AuthTokenRepository;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.entity.ConsentItemCode;
import com.example.nutriuniv.domain.consent.service.ConsentReader;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.service.GoalService;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 소셜 로그인 · 가입 (v3 ACCT_004 · ACCT_005).
 * <ul>
 *   <li>가입한 계정: 바로 토큰 + 기기 기록 병합(동의를 다시 받지 않는다)</li>
 *   <li>처음 로그인한 계정: 계정을 만들지 않고 가입 토큰(30분)만 준다 — 회사 계정 정보는 토큰에만 담고 저장하지 않는다.
 *       가입 동의 화면에서 「동의하고 시작하기」 → POST /auth/signup 이 계정 생성 · 동의 기록 · 목표 저장 · 병합을 한다</li>
 *   <li>필수 동의(만 14세 이상 · 이용약관 · 개인정보) 중 하나라도 빠지면 가입하지 않는다</li>
 *   <li>병합은 따로 커밋한다 — 실패해도 로그인은 끝나고 다음 로그인 때 다시 옮긴다</li>
 *   <li>계정 식별은 회사별 계정 식별자(같은 이메일이라도 합치지 않는다)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final JwtService jwtService;
    private final OAuthClientFactory oAuthClientFactory;
    private final AnonymousMergeService anonymousMergeService;
    private final ConsentService consentService;
    private final ConsentReader consentReader;
    private final GoalService goalService;

    // ── POST /auth/oauth ──────────────────────────────────────────────────────────

    @Transactional
    public TokenResponse login(OAuthLoginRequest request, Actor actor) {
        if (request.getProvider() == null || request.getProvider().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "provider는 필수입니다.");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "code는 필수입니다.");
        }

        OAuthClient client = oAuthClientFactory.getClient(request.getProvider());   // 지원 안 하는 provider → 400
        String providerAccessToken = client.getAccessToken(request.getCode());       // 토큰 교환 실패 → 401 LOGIN_FAILED
        OAuthUserInfo userInfo = client.getUserInfo(providerAccessToken);            // 유저 조회 실패 → 401 LOGIN_FAILED
        String provider = client.getProvider().lowerName();

        Optional<User> existing = userRepository.findByOauthProviderAndOauthIdAndIsActiveTrue(provider, userInfo.getOauthId());
        if (existing.isPresent()) {
            return loginExisting(existing.get(), actor);
        }

        // 처음 로그인 — 가입 동의 화면으로. 계정은 아직 만들지 않는다
        JwtService.SignupClaims claims = new JwtService.SignupClaims(provider, userInfo.getOauthId(), userInfo.getEmail(), userInfo.getName());
        return TokenResponse.builder()
                .signupRequired(true)
                .signupToken(jwtService.generateSignupToken(claims))
                .signupProfile(new TokenResponse.SignupProfile(provider.toUpperCase(), userInfo.getEmail(), userInfo.getName()))
                .build();
    }

    // ── POST /auth/signup ─────────────────────────────────────────────────────────

    @Transactional
    public TokenResponse signup(SignupRequest request, Actor actor, String userAgent) {
        JwtService.SignupClaims claims = request == null || request.signupToken() == null ? null
                : jwtService.parseSignupToken(request.signupToken());
        if (claims == null) {
            throw new CustomException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        Map<ConsentItemCode, Boolean> agreements = agreements(request.agreements());
        List<String> missing = Arrays.stream(ConsentItemCode.values())
                .filter(ConsentItemCode::required)
                .filter(c -> !Boolean.TRUE.equals(agreements.get(c)))
                .map(Enum::name).toList();
        if (!missing.isEmpty()) {
            throw new CustomException(ErrorCode.REQUIRED_CONSENT_MISSING, "필수 동의 항목: " + String.join(", ", missing));
        }
        boolean goalConsented = Boolean.TRUE.equals(agreements.get(ConsentItemCode.GOAL_ACCOUNT));
        GoalType goal = goalConsented && request.goal() != null && !request.goal().isBlank() ? GoalType.from(request.goal()) : null;
        ConsentService.Versions versions = consentService.checkVersions(request.termsVersion(), request.privacyVersion());

        // 같은 가입 토큰으로 두 번 눌렀으면 이미 만든 계정으로 로그인만
        Optional<User> existing = userRepository.findByOauthProviderAndOauthIdAndIsActiveTrue(claims.provider(), claims.oauthId());
        if (existing.isPresent()) {
            return loginExisting(existing.get(), actor);
        }

        User user = userRepository.save(User.register(claims.provider(), claims.oauthId(), claims.email(), claims.nickname()));
        user.assignDefaultNickname();
        user.confirmSignupConsents();
        consentService.recordSignup(user.getId(), agreements, versions, documentVersions(request.documentVersions()),
                actor.ipAddress(), userAgent);
        if (goal != null) {
            goalService.changeGoal(Owner.ofUser(user.getId()), goal);
        }
        log.info("[AUTH] 신규 가입 — provider={} userId={}", claims.provider(), user.getId());
        return issueToken(user, true, mergeQuietly(actor, user, goalConsented));
    }

    private TokenResponse loginExisting(User user, Actor actor) {
        boolean goalConsented = consentReader.isAgreed(user.getId(), ConsentItemCode.GOAL_ACCOUNT);
        return issueToken(user, false, mergeQuietly(actor, user, goalConsented));
    }

    private AnonymousMergeService.MergeResult mergeQuietly(Actor actor, User user, boolean goalConsented) {
        try {
            return anonymousMergeService.merge(actor.anonymousId(), user, goalConsented);
        } catch (RuntimeException e) {
            log.warn("[MERGE] 실패 — 로그인은 계속, 다음 로그인 때 다시 옮김 userId={}: {}", user.getId(), e.getMessage());
            return AnonymousMergeService.MergeResult.NONE;
        }
    }

    private static Map<ConsentItemCode, Boolean> agreements(Map<String, Boolean> raw) {
        Map<ConsentItemCode, Boolean> out = new EnumMap<>(ConsentItemCode.class);
        if (raw != null) raw.forEach((k, v) -> out.put(ConsentItemCode.from(k), Boolean.TRUE.equals(v)));
        return out;
    }

    private static Map<ConsentItemCode, String> documentVersions(Map<String, String> raw) {
        Map<ConsentItemCode, String> out = new EnumMap<>(ConsentItemCode.class);
        if (raw != null) raw.forEach((k, v) -> {
            if (v != null && !v.isBlank()) out.put(ConsentItemCode.from(k), v.trim());
        });
        return out;
    }

    // ── POST /auth/refresh ────────────────────────────────────────────────────────

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        AuthToken authToken = authTokenRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            authTokenRepository.delete(authToken);
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = authToken.getUser();
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId());

        authToken.rotate(newRefreshToken,
                LocalDateTime.now().plusNanos(jwtService.getRefreshExpMs() * 1_000_000));

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────────

    /** 토큰 필수(본인). 유저 단위 refresh 토큰 전체 삭제. */
    @Transactional
    public void logout(Actor actor) {
        if (!actor.isLoggedIn()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        authTokenRepository.deleteByUserId(actor.userId());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────────

    private TokenResponse issueToken(User user, boolean newUser, AnonymousMergeService.MergeResult merged) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        authTokenRepository.deleteByUserId(user.getId());
        authTokenRepository.save(AuthToken.create(
                user, refreshToken, null,
                LocalDateTime.now().plusNanos(jwtService.getRefreshExpMs() * 1_000_000)
        ));

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .newUser(newUser)
                .mergedRecordCount(merged.total())
                .merged(new TokenResponse.Merged(merged.savedProducts(), merged.goals(), merged.history(), merged.contributions(), merged.reports()))
                .user(TokenResponse.UserSummary.from(user))
                .build();
    }
}
