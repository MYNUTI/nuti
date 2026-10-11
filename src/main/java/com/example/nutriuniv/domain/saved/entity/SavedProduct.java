package com.example.nutriuniv.domain.saved.entity;

import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 저장한 제품 (기능명세서 7.1) — 구 '찜'(UserFavorite) 개조.
 * 소유자는 user_id XOR anonymous_id: 로그인·비로그인이 같은 API 를 쓰고 소유자만 다르다. 로그인 시 익명 행을 계정으로 병합(7.2).
 *
 * <p>물리 테이블명은 user_favorites 를 그대로 둔다 — ddl-auto=update 는 rename 을 못 하고, 배포 전에 수동 SQL 이
 * 반드시 먼저 돌아야 하는 구조는 사고 위험이 크다. user_id NOT NULL 해제·XOR CHECK·익명 유니크는 db/manual SQL.
 */
@Entity
@Table(name = "user_favorites",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}),
        indexes = @Index(name = "idx_user_favorites_anon", columnList = "anonymous_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SavedProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소유자 (둘 중 하나만) — 병합 시 anonymousId → userId 로 옮긴다
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "anonymous_id", length = 36)
    private String anonymousId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SavedProduct create(Owner owner, Product product) {
        SavedProduct s = new SavedProduct();
        s.userId = owner.userId();
        s.anonymousId = owner.anonymousId();
        s.product = product;
        return s;
    }

    /** 병합(7.2): 익명 소유를 계정으로. 계정에 같은 제품이 이미 있으면 호출부가 이 행을 삭제한다. */
    public void transferTo(Long userId) {
        this.userId = userId;
        this.anonymousId = null;
    }
}
