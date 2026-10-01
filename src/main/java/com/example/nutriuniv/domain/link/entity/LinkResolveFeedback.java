package com.example.nutriuniv.domain.link.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 링크 매칭 피드백 (API 명세 POST /links/resolve/{resolveId}/feedback) — 「맞아요·아니에요」와 바로잡은 제품.
 * url_hash 를 함께 두어 같은 링크의 다음 인식 때 바로잡은 제품을 먼저 쓴다(정확도 개선에 사용).
 */
@Entity
@Table(name = "link_resolve_feedbacks",
        indexes = {
                @Index(name = "idx_link_resolve_feedbacks_resolve", columnList = "resolve_id"),
                @Index(name = "idx_link_resolve_feedbacks_hash", columnList = "url_hash, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkResolveFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resolve_id", nullable = false, length = 36)
    private String resolveId;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "corrected_product_id")
    private Long correctedProductId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static LinkResolveFeedback create(LinkResolve resolve, boolean confirmed, Long correctedProductId, Owner owner) {
        LinkResolveFeedback f = new LinkResolveFeedback();
        f.resolveId          = resolve.getId();
        f.urlHash            = resolve.getUrlHash();
        f.confirmed          = confirmed;
        f.correctedProductId = correctedProductId;
        f.userId             = owner == null ? null : owner.userId();
        f.anonymousId        = owner == null ? null : owner.anonymousId();
        f.createdAt          = LocalDateTime.now();
        return f;
    }
}
