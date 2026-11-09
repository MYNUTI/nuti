package com.example.nutriuniv.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 제품 적재 기록 (v3 DATA_000) — 언제 어떤 파일을 넣었고 몇 건이 바뀌었는지.
 * 진행 중(RUNNING)인 동안에는 다른 적재와 등급 기준 재산출(CORE_007)을 막는다.
 */
@Entity
@Table(name = "product_loads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLoad {

    public enum Status { RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "total_products", nullable = false)
    private int totalProducts;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount;

    @Column(name = "protected_count", nullable = false)
    private int protectedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static ProductLoad start(String fileName) {
        ProductLoad l = new ProductLoad();
        l.fileName = fileName;
        l.status = Status.RUNNING;
        l.startedAt = LocalDateTime.now();
        return l;
    }

    public void finish(int total, int created, int updated, int unchanged, int protectedRows, int failed) {
        this.status = Status.DONE;
        this.totalProducts = total;
        this.createdCount = created;
        this.updatedCount = updated;
        this.unchangedCount = unchanged;
        this.protectedCount = protectedRows;
        this.failedCount = failed;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = Status.FAILED;
        this.finishedAt = LocalDateTime.now();
    }
}
