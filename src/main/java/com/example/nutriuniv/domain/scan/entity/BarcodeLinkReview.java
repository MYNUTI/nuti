package com.example.nutriuniv.domain.scan.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 운영 화면 「확인 대기」 (v3 SCAN_008 · SCAN_009) — 서로 다른 2명 이상이 같은 바코드에 같은 제품을 고르면 한 줄.
 * <ul>
 *   <li>CONFIRM 선택: 들어올 때 바코드를 그 제품에 바로 잇는다(모두에게 결과). 운영자가 맞다 → 확정, 아니다 → 연결을 끊고 선택을 지워 다시 묻는다</li>
 *   <li>MULTIPLE 선택: 운영자가 맞다 → 그 바코드를 고른 제품에만 남긴다(바뀐 바코드 정리), 아니다 → 그대로 둔다</li>
 * </ul>
 */
@Entity
@Table(name = "barcode_link_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_barcode_link_reviews", columnNames = {"barcode", "product_id"}),
        indexes = @Index(name = "idx_barcode_link_reviews_status", columnList = "status, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BarcodeLinkReview {

    public enum Status { PENDING, CONFIRMED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String barcode;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BarcodePick.Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** 선택으로 바코드를 이 제품에 새로 이었는지 — 아니라고 하면 이 연결만 끊는다. */
    @Column(name = "linked_by_picks", nullable = false)
    private boolean linkedByPicks;

    @Column(nullable = false)
    private long pickers;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public static BarcodeLinkReview pending(String barcode, Long productId, BarcodePick.Kind kind, boolean linked, long pickers) {
        BarcodeLinkReview r = new BarcodeLinkReview();
        r.barcode = barcode;
        r.productId = productId;
        r.reopen(kind, linked, pickers);
        return r;
    }

    public void reopen(BarcodePick.Kind kind, boolean linked, long pickers) {
        this.kind = kind;
        this.linkedByPicks = linked;
        this.pickers = pickers;
        this.status = Status.PENDING;
        this.adminUserId = null;
        this.decidedAt = null;
        this.createdAt = LocalDateTime.now();
    }

    public void updatePickers(long pickers) {
        this.pickers = pickers;
    }

    public void decide(boolean confirmed, Long adminUserId) {
        this.status = confirmed ? Status.CONFIRMED : Status.REJECTED;
        this.adminUserId = adminUserId;
        this.decidedAt = LocalDateTime.now();
    }
}
