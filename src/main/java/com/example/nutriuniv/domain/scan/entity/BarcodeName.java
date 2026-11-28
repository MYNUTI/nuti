package com.example.nutriuniv.domain.scan.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이름을 아는 바코드 (v3 SCAN_010) — 우리 제품에는 없지만 바코드에 등록된 제품명 · 회사를 아는 경우.
 * 「비슷한 후보」(같은 회사 · 이름이 비슷한 제품)와 「이름만 아는 바코드」 분기에 쓴다. 운영자가 CSV 로 채운다.
 */
@Entity
@Table(name = "barcode_names", uniqueConstraints = @UniqueConstraint(name = "uk_barcode_names_barcode", columnNames = "barcode"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BarcodeName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String company;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static BarcodeName of(String barcode14, String name, String company) {
        BarcodeName b = new BarcodeName();
        b.barcode = barcode14;
        b.update(name, company);
        return b;
    }

    public void update(String name, String company) {
        this.name = name;
        this.company = company;
        this.updatedAt = LocalDateTime.now();
    }
}
