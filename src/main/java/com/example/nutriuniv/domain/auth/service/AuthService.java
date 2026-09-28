package com.example.nutriuniv.domain.auth.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.JwtService;
import com.example.nutriuniv.domain.auth.client.OAuthClient;
import com.example.nutriuniv.domain.auth.client.OAuthClientFactory;
import com.example.nutriuniv.domain.auth.client.OAuthUserInfo;
import com.example.nutriuniv.domain.auth.dto.LogoutResponse;
import com.example.nutriuniv.domain.auth.dto.OAuthLoginRequest;
import com.example.nutriuniv.domain.auth.dto.RefreshRequest;
import com.example.nutriuniv.domain.auth.dto.TokenResponse;
import com.example.nutriuniv.domain.auth.entity.AuthToken;
import com.example.nutriuniv.domain.auth.repository.AuthTokenRepository;
import com.example.nutriuniv.domain.consent.service.ConsentService;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 소셜 로그인 (API 명세 /auth/oauth 「기존 수정」, 기능명세서 7.2 병합 내장).
 * <ul>
 *   <li>신규회원도 즉시 가입 + 토큰 — 등록 단계(/auth/register) 폐지. 이메일 외 프로필은 받지 않는다</li>
 *   <li>is_active=false(탈퇴) 유저는 신규회원으로 처리 (재가입 정책은 팀 결정 대기 — 기존 동작 유지)</li>
 *   <li>헤더 X-Anonymous-Id 가 있으면 저장·목표·기여를 계정으로 병합해 mergedRecordCount 로 돌려준다 — 한 트랜잭션</li>
 *   <li>로그아웃은 refresh 토큰을 유저 단위로 전부 지우고 새 익명 ID 를 발급한다(병합된 옛 ID 는 더 못 쓴다)</li>
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

        // 활성 유저만 조회 → 탈퇴(is_active=false) 유저는 신규회원으로
        Optional<User> existing = userRepository.findByOauthProviderAndOauthIdAndIsActiveTrue(provider, userInfo.getOauthId());
        boolean newUser = existing.isEmpty();
        User user = existing.orElseGet(() -> register(provider, userInfo));

        // 7.2 병합 — 저장·목표·기여(+동의·온보딩 상태). 미발급·이미 병합된 ID 는 무시하고 로그인만
        AnonymousMergeService.MergeResult merged = anonymousMergeService.merge(actor.anonymousId(), user);

        return issueToken(user, newUser, merged.total());
    }

    /** 즉시 가입 — 이메일만 받고, 닉네임은 서버가 「사용자{id}」로 정한다. 동의는 병합(익명 동의 승계)이나 이후 동의 API 에서. */
    private User register(String provider, OAuthUserInfo userInfo) {
        User user = userRepository.save(User.register(provider, userInfo.getOauthId(), userInfo.getEmail()));
        user.assignDefaultNickname();
        log.info("[AUTH] 신규 가입 — provider={} userId={}", provider, user.getId());
        return user;
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

    /** 토큰 필수(본인). 유저 단위 refresh 토큰 전체 삭제 + 새 익명 ID 발급·반환. */
    @Transactional
    public LogoutResponse logout(Actor actor) {
        if (!actor.isLoggedIn()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        authTokenRepository.deleteByUserId(actor.userId());
        String anonymousId = consentService.issueAnonymousAfterLogout(actor.userId());
        return new LogoutResponse(anonymousId);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────────

    private TokenResponse issueToken(User user, boolean newUser, int mergedRecordCount) {
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
                .mergedRecordCount(mergedRecordCount)
                .user(TokenResponse.UserSummary.from(user))
                .build();
    }
}
