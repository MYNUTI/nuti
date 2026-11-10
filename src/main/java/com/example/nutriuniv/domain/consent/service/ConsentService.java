package com.example.nutriuniv.domain.consent.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.body.service.BodyProfileService;
import com.example.nutriuniv.domain.consent.dto.ConsentChangeRequest;
import com.example.nutriuniv.domain.consent.dto.ConsentItemsResponse;
import com.example.nutriuniv.domain.consent.dto.ConsentsResponse;
import com.example.nutriuniv.domain.consent.entity.ConsentItemCode;
import com.example.nutriuniv.domain.consent.entity.ConsentRecord;
import com.example.nutriuniv.domain.consent.entity.Policy;
import com.example.nutriuniv.domain.consent.entity.PolicyType;
import com.example.nutriuniv.domain.consent.repository.ConsentRecordRepository;
import com.example.nutriuniv.domain.consent.repository.PolicyRepository;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.goal.service.GoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 동의 (v3 ACCT_004 · ACCT_007 · ONB_009) — 계정만 동의한다.
 * <ul>
 *   <li>항목 6개(필수 3 · 선택 3)를 항목마다 기록한다: 동의 여부 · 시각 · 약관 판 · 처리방침 판 · 동의문 판. 추가만 하고 고치지 않는다</li>
 *   <li>필수 항목은 가입 때만 받고 바꿀 수 없다(바꾸려면 계정 삭제). 선택 항목은 언제든 켜고 끈다</li>
 *   <li>철회하면 그 동의로 저장한 데이터를 바로 지운다 — 목표 저장: 계정 목표 / 몸 정보: 몸 정보 · 칼로리 구간 / 광고성: 기록만</li>
 *   <li>IP 는 원본 대신 SHA-256 해시</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRecordRepository recordRepository;
    private final PolicyRepository policyRepository;
    private final ConsentReader consentReader;
    private final GoalService goalService;
    private final BodyProfileService bodyProfileService;

    public record Versions(String terms, String privacy) {}

    // ── 가입 (AuthService 가 부른다) ────────────────────────────────────────────────

    /** 가입 동의 6항목을 한 번에 기록. 필수 항목 검사는 호출부(가입)가 먼저 한다. */
    @Transactional
    public void recordSignup(Long userId, Map<ConsentItemCode, Boolean> agreements, Versions versions,
                             Map<ConsentItemCode, String> documentVersions, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        String ipHash = sha256(ip);
        List<ConsentRecord> rows = new ArrayList<>();
        for (ConsentItemCode item : ConsentItemCode.values()) {
            boolean agreed = Boolean.TRUE.equals(agreements.get(item));
            rows.add(ConsentRecord.of(userId, item, agreed, versions.terms(), versions.privacy(),
                    documentVersions == null ? null : documentVersions.get(item), ipHash, userAgent, now));
        }
        recordRepository.saveAll(rows);
    }

    /**
     * 요청의 약관 · 처리방침 판이 지금 판과 같은지. 다르면 409 POLICY_VERSION_CONFLICT.
     * 정책 행이 아직 없는 환경이면 요청 값을 그대로 쓴다(경고만).
     */
    @Transactional(readOnly = true)
    public Versions checkVersions(String termsVersion, String privacyVersion) {
        return new Versions(check(PolicyType.TERMS, termsVersion), check(PolicyType.PRIVACY, privacyVersion));
    }

    // ── GET /me/consents ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ConsentsResponse list(Actor actor) {
        return response(requireUser(actor));
    }

    // ── PUT /me/consents/{item} ───────────────────────────────────────────────────

    @Transactional
    public ConsentsResponse change(Actor actor, String itemParam, ConsentChangeRequest req, String userAgent) {
        Long userId = requireUser(actor);
        ConsentItemCode item = ConsentItemCode.from(itemParam);
        if (item.required()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "필수 동의는 바꿀 수 없어요. 그만 쓰려면 계정을 삭제해 주세요.");
        }
        if (req == null || req.agreed() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "agreed(true · false)는 필수입니다.");
        }
        boolean agreed = req.agreed();
        GoalType goal = agreed && item == ConsentItemCode.GOAL_ACCOUNT && req.goal() != null && !req.goal().isBlank()
                ? GoalType.from(req.goal()) : null;                     // 허용값 외 400 — 기록 전에 검사

        boolean now = consentReader.isAgreed(userId, item);
        if (now != agreed) {
            Versions v = currentVersions();
            recordRepository.save(ConsentRecord.of(userId, item, agreed, v.terms(), v.privacy(),
                    blankToNull(req.documentVersion()), sha256(actor.ipAddress()), userAgent, LocalDateTime.now()));
        }

        Owner owner = Owner.ofUser(userId);
        if (!agreed) {
            switch (item) {
                case GOAL_ACCOUNT -> goalService.clearSavedGoal(owner);      // 기기 목표는 기기에 남는다
                case BODY_PROFILE -> bodyProfileService.deleteFor(userId);  // 칼로리 구간은 기본 2,000 으로
                default -> { }
            }
        } else {
            if (goal != null) goalService.changeGoal(owner, goal);
            if (item == ConsentItemCode.BODY_PROFILE && req.bodyProfile() != null) {
                bodyProfileService.saveFor(userId, req.bodyProfile());
            }
        }
        return response(userId);
    }

    // ── GET /meta/consent-items ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ConsentItemsResponse items() {
        Versions v = currentVersions();
        List<ConsentItemsResponse.Item> items = new ArrayList<>();
        for (ConsentItemCode c : ConsentItemCode.values()) items.add(new ConsentItemsResponse.Item(c.name(), c.label(), c.required()));
        return new ConsentItemsResponse(v.terms(), v.privacy(), items);
    }

    /** 지금 처리방침 판 (bootstrap). 정책 행이 없으면 null. */
    @Transactional(readOnly = true)
    public String currentPolicyVersion() {
        return currentVersions().privacy();
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private ConsentsResponse response(Long userId) {
        Map<ConsentItemCode, ConsentRecord> latest = consentReader.latest(userId);
        List<ConsentsResponse.Item> items = new ArrayList<>();
        for (ConsentItemCode c : ConsentItemCode.values()) {
            ConsentRecord r = latest.get(c);
            items.add(new ConsentsResponse.Item(c.name(), c.label(), c.required(), r != null && r.isAgreed(),
                    r == null ? null : r.getCreatedAt(), r == null ? null : r.getTermsVersion(),
                    r == null ? null : r.getPrivacyVersion(), r == null ? null : r.getDocumentVersion()));
        }
        return new ConsentsResponse(items);
    }

    /** 동의 관리는 로그인 전용(ACCT_001). */
    private static Long requireUser(Actor actor) {
        if (!actor.isLoggedIn()) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return actor.userId();
    }

    private Versions currentVersions() {
        return new Versions(current(PolicyType.TERMS).map(Policy::getVersion).orElse(null),
                current(PolicyType.PRIVACY).map(Policy::getVersion).orElse(null));
    }

    private Optional<Policy> current(PolicyType type) {
        return policyRepository.findByPolicyTypeAndIsCurrentTrue(type);
    }

    private String check(PolicyType type, String requested) {
        String req = blankToNull(requested);
        Optional<Policy> cur = current(type);
        if (cur.isEmpty()) {
            if (req != null) log.warn("[Consent] {} 정책의 현재 판이 DB 에 없음 — 요청 판 '{}' 을(를) 그대로 기록", type, req);
            return req;
        }
        if (req != null && !cur.get().getVersion().equals(req)) {
            throw new CustomException(ErrorCode.POLICY_VERSION_CONFLICT,
                    (type == PolicyType.TERMS ? "약관" : "처리방침") + " 판이 바뀌었습니다. 지금 판: " + cur.get().getVersion());
        }
        return cur.get().getVersion();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String sha256(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
