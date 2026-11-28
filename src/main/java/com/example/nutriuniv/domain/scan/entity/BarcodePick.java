package com.example.nutriuniv.domain.scan.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 바코드에 대한 기기(계정)별 선택 (v3 SCAN_008 · SCAN_009).
 * <ul>
 *   <li>CONFIRM — 「이 제품인가요？」에 맞아요(또는 아니에요 → 검색에서 고른 제품)</li>
 *   <li>MULTIPLE — 바코드에 제품이 여러 개일 때 고른 제품</li>
 * </ul>
 * 같은 기기는 바코드마다 한 줄(다시 고르면 바뀐다). 다음 스캔부터 이 선택으로 바로 결과를 연다.
 */
@Entity
@Table(name = "barcode_picks",
        uniqueConstraints = @UniqueConstraint(name = "uk_barcode_picks_owner", columnNames = {"barcode", "owner_key"}),
        indexes = @Index(name = "idx_barcode_picks_barcode_product", columnList = "barcode, product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BarcodePick {

    public enum Kind { CONFIRM, MULTIPLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String barcode;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** u:{userId} 또는 a:{anonymousId} — 서로 다른 사람 수를 센다. */
    @Column(name = "owner_key", nullable = false, length = 80)
    private String ownerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static BarcodePick of(String barcode, Long productId, String ownerKey, Kind kind) {
        BarcodePick p = new BarcodePick();
        p.barcode = barcode;
        p.ownerKey = ownerKey;
        p.change(productId, kind);
        return p;
    }

    public void change(Long productId, Kind kind) {
        this.productId = productId;
        this.kind = kind;
        this.updatedAt = LocalDateTime.now();
    }
}
