package com.example.nutriuniv.domain.consent.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.dto.ConsentRequest;
import com.example.nutriuniv.domain.consent.dto.ConsentResponse;
import com.example.nutriuniv.domain.consent.dto.HealthConsentResponse;
import com.example.nutriuniv.domain.consent.entity.Consent;
import com.example.nutriuniv.domain.consent.entity.ConsentAction;
import com.example.nutriuniv.domain.consent.entity.ConsentType;
import com.example.nutriuniv.domain.consent.entity.Policy;
import com.example.nutriuniv.domain.consent.entity.PolicyType;
import com.example.nutriuniv.domain.consent.repository.ConsentRepository;
import com.example.nutriuniv.domain.consent.repository.PolicyRepository;
import com.example.nutriuniv.domain.goal.service.GoalService;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 개인정보 동의(1.2)·건강정보 동의(1.3) — <b>로그인한 계정만</b>.
 *
 * <ul>
 *   <li>v3(ACCT_001·ACCT_004): 기기 ID 는 동의 없이 쓰고, 동의는 가입(계정)에서만 받는다. 그래서 익명 ID 를 발급하지도, 익명 소유 동의를 남기지도 않는다.</li>
 *   <li>같은 버전 재요청은 200 멱등. 다른 버전이면 재동의 행 추가.</li>
 *   <li>동의 기록은 append-only. IP 는 해시만.</li>
 *   <li>건강정보 동의는 개인정보 동의와 저장 분리. 철회 시 정보 삭제(플래그 off + REVOKE 행) + 서버에 저장한 목표 삭제.</li>
 * </ul>
 * 동의 7항목·가입 2단계(ACCT_004)로 바꾸는 작업은 계정 브랜치에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final GoalService goalService;

    // 필수 동의 항목 코드 — 항목 목록은 팀 확정 사항이라 설정으로 뺐다. 만 14세 확인은 register 폐지로 여기서 받는다.
    @Value("${app.consent.required-items:PERSONAL_INFO,AGE_OVER_14}")
    private String requiredItemsCsv;

    // ── POST /onboarding/consent (개인정보 동의 — 로그인한 계정) ──────────────────────

    @Transactional
    public ConsentResponse consentPrivacy(Actor actor, ConsentRequest request, String userAgent) {
        Owner owner = requireUser(actor);
        String version = checkPolicyVersion(PolicyType.PRIVACY, request.getPolicyVersion());
        Set<String> items = normalizeItems(request.getAgreedItems());
        requireItems(items);

        Optional<Consent> same = latest(owner, ConsentType.PRIVACY)
                .filter(c -> c.getAction() == ConsentAction.AGREE && version.equals(c.getPolicyVersion()));
        if (same.isPresent()) {
            return ConsentResponse.of(same.get().getCreatedAt());
        }
        Consent saved = consentRepository.save(
                Consent.agree(owner, ConsentType.PRIVACY, version, items, sha256(actor.ipAddress()), userAgent));
        userRepository.findById(actor.userId()).ifPresent(User::agreePersonalInfo);
        return ConsentResponse.of(saved.getCreatedAt());
    }

    // ── /me/health-consent (건강정보 동의 3종) ──────────────────────────────────────

    @Transactional
    public HealthConsentResponse agreeHealth(Actor actor, ConsentRequest request, String userAgent) {
        Owner owner = requireUser(actor);
        String version = checkPolicyVersion(PolicyType.HEALTH, request.getPolicyVersion());
        Set<String> items = normalizeItems(request.getAgreedItems());

        Optional<Consent> same = latest(owner, ConsentType.HEALTH)
                .filter(c -> c.getAction() == ConsentAction.AGREE && version.equals(c.getPolicyVersion()));
        if (same.isPresent()) {
            return HealthConsentResponse.agreed(same.get().getCreatedAt());   // 멱등
        }
        Consent saved = consentRepository.save(
                Consent.agree(owner, ConsentType.HEALTH, version, items, sha256(actor.ipAddress()), userAgent));
        setHealthFlag(owner, true);
        return HealthConsentResponse.agreed(saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public HealthConsentResponse getHealth(Actor actor) {
        Owner owner = requireUser(actor);
        Optional<Consent> last = latest(owner, ConsentType.HEALTH);
        if (last.isPresent() && last.get().getAction() == ConsentAction.AGREE) {
            return HealthConsentResponse.agreed(last.get().getCreatedAt());
        }
        return HealthConsentResponse.notAgreed();                          // 「동의 안 함」은 정상 응답
    }

    @Transactional
    public HealthConsentResponse revokeHealth(Actor actor, String userAgent) {
        Owner owner = requireUser(actor);
        Optional<Consent> last = latest(owner, ConsentType.HEALTH);
        if (last.isEmpty() || last.get().getAction() == ConsentAction.REVOKE) {
            return HealthConsentResponse.notAgreed();                      // 이미 철회/미동의 → 멱등
        }
        consentRepository.save(Consent.revoke(owner, ConsentType.HEALTH, last.get().getPolicyVersion(),
                sha256(actor.ipAddress()), userAgent));
        setHealthFlag(owner, false);
        goalService.clearSavedGoal(owner);     // 서버에 저장한 목표는 건강정보 동의가 전제 → 철회 시 지운다
        return HealthConsentResponse.notAgreed();
    }

    // ── 조회 헬퍼 (bootstrap 등에서 사용) — 기기 ID 소유자는 동의가 없으므로 false ─────────

    @Transactional(readOnly = true)
    public boolean isHealthConsented(Owner owner) {
        if (!owner.isUser()) {
            return false;
        }
        return latest(owner, ConsentType.HEALTH)
                .map(c -> c.getAction() == ConsentAction.AGREE)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPrivacyConsented(Owner owner) {
        if (!owner.isUser()) {
            return false;
        }
        return latest(owner, ConsentType.PRIVACY)
                .map(c -> c.getAction() == ConsentAction.AGREE)
                .orElse(false);
    }

    /** 현재 개인정보 처리방침 버전. 아직 정책 행이 없으면 null. */
    @Transactional(readOnly = true)
    public String currentPolicyVersion() {
        return policyRepository.findByPolicyTypeAndIsCurrentTrue(PolicyType.PRIVACY).map(Policy::getVersion).orElse(null);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    /** 동의 관리는 로그인 전용(ACCT_001 「로그인이 필요한 요청은 알림, 동의 관리, 로그아웃, 계정 삭제뿐」). */
    private static Owner requireUser(Actor actor) {
        if (!actor.isLoggedIn()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return Owner.ofUser(actor.userId());
    }

    private Optional<Consent> latest(Owner owner, ConsentType type) {
        return consentRepository.findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(owner.userId(), type);
    }

    private void setHealthFlag(Owner owner, boolean agreed) {
        userRepository.findById(owner.userId()).ifPresent(u -> {
            if (agreed) u.agreeHealthInfo(); else u.revokeHealthInfo();
        });
    }

    /**
     * 요청의 policyVersion 이 현재 버전과 다르면 409 POLICY_VERSION_CONFLICT.
     * 정책 행이 아직 없는 환경(초기 개발)에서는 요청 버전을 그대로 받아들이고 경고만 남긴다 — db/manual SQL 로 시드할 것.
     */
    private String checkPolicyVersion(PolicyType type, String requested) {
        if (requested == null || requested.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "policyVersion은 필수입니다.");
        }
        Optional<Policy> current = policyRepository.findByPolicyTypeAndIsCurrentTrue(type);
        if (current.isEmpty()) {
            log.warn("[Consent] {} 정책의 현재 버전이 DB 에 없음 — 요청 버전 '{}' 을(를) 그대로 기록", type, requested);
            return requested.trim();
        }
        if (!current.get().getVersion().equals(requested.trim())) {
            throw new CustomException(ErrorCode.POLICY_VERSION_CONFLICT,
                    "처리방침 버전이 바뀌었습니다. 현재 버전: " + current.get().getVersion());
        }
        return current.get().getVersion();
    }

    private Set<String> normalizeItems(List<String> raw) {
        if (raw == null) {
            return new LinkedHashSet<>();
        }
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireItems(Set<String> items) {
        List<String> missing = Arrays.stream(requiredItemsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase)
                .filter(code -> !items.contains(code))
                .toList();
        if (!missing.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "필수 동의 항목이 누락되었습니다: " + String.join(", ", missing));
        }
    }

    private static String sha256(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
