package com.example.nutriuniv.domain.history.entity;

import com.example.nutriuniv.common.security.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 최근 본 제품 기록 (v3 RTN_001) — 결과 화면을 열 때마다 한 줄. 소유자는 user_id XOR anonymous_id, 로그인 때 계정으로 옮긴다.
 * 목록은 제품별 마지막으로 본 시각으로 묶어 보여 준다.
 */
@Entity
@Table(name = "view_history", indexes = {
        @Index(name = "idx_view_history_user", columnList = "user_id, viewed_at"),
        @Index(name = "idx_view_history_anon", columnList = "anonymous_id, viewed_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ViewSource source;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    public static ViewHistory of(Owner owner, Long productId, ViewSource source, LocalDateTime at) {
        ViewHistory h = new ViewHistory();
        h.userId = owner.userId();
        h.anonymousId = owner.anonymousId();
        h.productId = productId;
        h.source = source;
        h.viewedAt = at;
        return h;
    }
}
