package com.example.nutriuniv.domain.consent.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.dto.ConsentRequest;
import com.example.nutriuniv.domain.consent.dto.ConsentResponse;
import com.example.nutriuniv.domain.consent.dto.HealthConsentResponse;
import com.example.nutriuniv.domain.consent.entity.AnonymousUser;
import com.example.nutriuniv.domain.consent.entity.Consent;
import com.example.nutriuniv.domain.consent.entity.ConsentAction;
import com.example.nutriuniv.domain.consent.entity.ConsentItem;
import com.example.nutriuniv.domain.consent.entity.ConsentType;
import com.example.nutriuniv.domain.consent.entity.Policy;
import com.example.nutriuniv.domain.consent.entity.PolicyType;
import com.example.nutriuniv.domain.consent.repository.AnonymousUserRepository;
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
 * 개인정보 동의(1.2)·건강정보 동의(1.3).
 *
 * <ul>
 *   <li>개인정보 동의가 익명 ID 를 <b>발급하는 유일한 지점</b>. 진입 시 발급 금지.</li>
 *   <li>같은 버전 재요청은 200 멱등(새 ID 를 만들지 않음). 다른 버전이면 재동의 행 추가.</li>
 *   <li>동의 기록은 append-only. IP 는 해시만.</li>
 *   <li>건강정보 동의는 개인정보 동의와 저장 분리. 철회 시 정보 삭제(플래그 off + REVOKE 행) + 목표를 GENERAL 로.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final AnonymousUserRepository anonymousUserRepository;
    private final ConsentRepository consentRepository;
    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final OwnerResolver ownerResolver;
    private final GoalService goalService;

    // 필수 동의 항목 코드 — 항목 목록은 팀 확정 사항이라 설정으로 뺐다. 만 14세 확인은 register 폐지로 여기서 받는다.
    @Value("${app.consent.required-items:PERSONAL_INFO,AGE_OVER_14}")
    private String requiredItemsCsv;

    // ── POST /onboarding/consent (개인정보 동의 → 익명 ID 발급) ──────────────────────

    @Transactional
    public ConsentResponse consentPrivacy(Actor actor, ConsentRequest request, String userAgent) {
        String version = checkPolicyVersion(PolicyType.PRIVACY, request.getPolicyVersion());
        Set<String> items = normalizeItems(request.getAgreedItems());
        requireItems(items);
        String ipHash = sha256(actor.ipAddress());

        // 로그인 상태에서의 (재)동의 — 계정에 귀속, 익명 ID 는 새로 만들지 않는다
        if (actor.isLoggedIn()) {
            Owner owner = Owner.ofUser(actor.userId());
            Optional<Consent> same = latest(owner, ConsentType.PRIVACY)
                    .filter(c -> c.getAction() == ConsentAction.AGREE && version.equals(c.getPolicyVersion()));
            if (same.isPresent()) {
                return ConsentResponse.of(actor.anonymousId(), same.get().getCreatedAt());
            }
            Consent saved = consentRepository.save(Consent.agree(owner, ConsentType.PRIVACY, version, items, ipHash, userAgent));
            userRepository.findById(actor.userId()).ifPresent(User::agreePersonalInfo);
            return ConsentResponse.of(actor.anonymousId(), saved.getCreatedAt());
        }

        // 기존 익명 ID(서버 발급·미병합)의 재동의 — 같은 버전이면 멱등
        AnonymousUser anon = actor.hasAnonymousId()
                ? anonymousUserRepository.findByAnonymousId(actor.anonymousId()).filter(a -> !a.isMerged()).orElse(null)
                : null;
        if (anon != null) {
            Owner owner = Owner.ofAnonymous(anon.getAnonymousId());
            Optional<Consent> same = latest(owner, ConsentType.PRIVACY)
                    .filter(c -> c.getAction() == ConsentAction.AGREE && version.equals(c.getPolicyVersion()));
            if (same.isPresent()) {
                return ConsentResponse.of(anon.getAnonymousId(), same.get().getCreatedAt());
            }
            Consent saved = consentRepository.save(Consent.agree(owner, ConsentType.PRIVACY, version, items, ipHash, userAgent));
            anon.agreePersonalInfo();
            return ConsentResponse.of(anon.getAnonymousId(), saved.getCreatedAt());
        }

        // 신규 (또는 서버가 발급한 적 없는/이미 병합된 ID 를 보낸 경우) → 새 익명 ID 발급
        if (actor.hasAnonymousId()) {
            log.info("[Consent] 미발급·병합된 익명 ID 로 동의 요청 → 새 ID 발급: {}", actor.anonymousId());
        }
        AnonymousUser issued = anonymousUserRepository.save(AnonymousUser.issue());
        Consent saved = consentRepository.save(
                Consent.agree(Owner.ofAnonymous(issued.getAnonymousId()), ConsentType.PRIVACY, version, items, ipHash, userAgent));
        return ConsentResponse.of(issued.getAnonymousId(), saved.getCreatedAt());
    }

    // ── 로그아웃 후 새 익명 ID (API 명세 /auth/logout) ──────────────────────────────

    /**
     * 익명 ID 발급의 두 번째 지점 — 병합된 옛 ID 는 더 못 쓰므로 로그아웃한 기기에 새 ID 를 준다.
     * 방금 로그아웃한 사람은 계정으로 개인정보 동의를 한 사람이므로, 그 최신 동의(버전·항목·증빙)를 새 익명 소유로 한 줄 덧붙여 원장을 맞춘다.
     * 1차(register) 회원처럼 원장 행이 없으면 플래그만으로 발급한다.
     */
    @Transactional
    public String issueAnonymousAfterLogout(Long userId) {
        AnonymousUser issued = anonymousUserRepository.save(AnonymousUser.issue());
        latest(Owner.ofUser(userId), ConsentType.PRIVACY)
                .filter(c -> c.getAction() == ConsentAction.AGREE)
                .ifPresent(src -> consentRepository.save(Consent.agree(
                        Owner.ofAnonymous(issued.getAnonymousId()), ConsentType.PRIVACY, src.getPolicyVersion(),
                        src.getItems().stream().map(ConsentItem::getItemCode).toList(),
                        src.getIpHash(), src.getUserAgent())));
        return issued.getAnonymousId();
    }

    // ── /me/health-consent (건강정보 동의 3종) ──────────────────────────────────────

    @Transactional
    public HealthConsentResponse agreeHealth(Actor actor, ConsentRequest request, String userAgent) {
        Owner owner = ownerResolver.resolve(actor);                     // 동의 전 → 403 CONSENT_REQUIRED
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
        Owner owner = ownerResolver.resolve(actor);
        Optional<Consent> last = latest(owner, ConsentType.HEALTH);
        if (last.isPresent() && last.get().getAction() == ConsentAction.AGREE) {
            return HealthConsentResponse.agreed(last.get().getCreatedAt());
        }
        return HealthConsentResponse.notAgreed();                          // 「동의 안 함」은 정상 응답
    }

    @Transactional
    public HealthConsentResponse revokeHealth(Actor actor, String userAgent) {
        Owner owner = ownerResolver.resolve(actor);
        Optional<Consent> last = latest(owner, ConsentType.HEALTH);
        if (last.isEmpty() || last.get().getAction() == ConsentAction.REVOKE) {
            return HealthConsentResponse.notAgreed();                      // 이미 철회/미동의 → 멱등
        }
        consentRepository.save(Consent.revoke(owner, ConsentType.HEALTH, last.get().getPolicyVersion(),
                sha256(actor.ipAddress()), userAgent));
        setHealthFlag(owner, false);
        goalService.resetToGeneral(owner);     // 감량·근육증가 목표는 건강정보 동의가 전제 → 철회 시 GENERAL 로
        return HealthConsentResponse.notAgreed();
    }

    // ── 조회 헬퍼 (bootstrap·목표 설정 등에서 사용) ───────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isHealthConsented(Owner owner) {
        return latest(owner, ConsentType.HEALTH)
                .map(c -> c.getAction() == ConsentAction.AGREE)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPrivacyConsented(Owner owner) {
        if (!owner.isUser()) {
            return true;                                                     // 익명 ID 는 동의 시에만 발급되므로 존재 = 동의
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

    private Optional<Consent> latest(Owner owner, ConsentType type) {
        return owner.isUser()
                ? consentRepository.findTopByUserIdAndConsentTypeOrderByCreatedAtDesc(owner.userId(), type)
                : consentRepository.findTopByAnonymousIdAndConsentTypeOrderByCreatedAtDesc(owner.anonymousId(), type);
    }

    private void setHealthFlag(Owner owner, boolean agreed) {
        if (owner.isUser()) {
            userRepository.findById(owner.userId()).ifPresent(u -> {
                if (agreed) u.agreeHealthInfo(); else u.revokeHealthInfo();
            });
        } else {
            anonymousUserRepository.findByAnonymousId(owner.anonymousId()).ifPresent(a -> {
                if (agreed) a.agreeHealthInfo(); else a.revokeHealthInfo();
            });
        }
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
