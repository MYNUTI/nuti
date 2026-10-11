package com.example.nutriuniv.domain.link.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 링크 인식 결과 (API 명세 POST /links/resolve). 정규화 URL 의 해시로 24시간 캐시 — 같은 링크는 외부 조회 없이 같은 resolveId 를 돌려준다.
 * 피드백(LinkResolveFeedback)이 이 id 를 가리킨다. 추적 파라미터를 뗀 URL 만 저장한다.
 */
@Entity
@Table(name = "link_resolves",
        indexes = @Index(name = "idx_link_resolves_hash", columnList = "url_hash, expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkResolve {

    /** 매칭 방법 — 정확도 개선의 근거. */
    public enum Method { FEEDBACK, COUPANG_ID, EXACT_NAME, SIMILAR, NONE }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "url_canonical", nullable = false, length = 1000)
    private String urlCanonical;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "source_type", nullable = false, length = 10)
    private String sourceType;              // COUPANG | UNKNOWN

    @Column(name = "parsed_title", length = 255)
    private String parsedTitle;

    @Column(name = "coupang_product_id", length = 100)
    private String coupangProductId;

    @Column(name = "matched_product_id")
    private Long matchedProductId;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, length = 20)
    private Method matchMethod = Method.NONE;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static LinkResolve create(String urlCanonical, String urlHash, String sourceType, String parsedTitle, String coupangProductId,
                                     Long matchedProductId, BigDecimal confidence, Method method,
                                     Owner owner, String sessionId, LocalDateTime now, LocalDateTime expiresAt) {
        LinkResolve r = new LinkResolve();
        r.id               = UUID.randomUUID().toString();
        r.urlCanonical     = urlCanonical.length() > 1000 ? urlCanonical.substring(0, 1000) : urlCanonical;
        r.urlHash          = urlHash;
        r.sourceType       = sourceType;
        r.parsedTitle      = parsedTitle == null ? null : (parsedTitle.length() > 255 ? parsedTitle.substring(0, 255) : parsedTitle);
        r.coupangProductId = coupangProductId;
        r.matchedProductId = matchedProductId;
        r.confidence       = confidence;
        r.matchMethod      = method == null ? Method.NONE : method;
        r.userId           = owner == null ? null : owner.userId();
        r.anonymousId      = owner == null ? null : owner.anonymousId();
        r.sessionId        = sessionId;
        r.createdAt        = now;
        r.expiresAt        = expiresAt;
        return r;
    }

    public boolean isMatched() {
        return matchedProductId != null;
    }
}
