package com.example.nutriuniv.domain.publicdata.entity;

import com.example.nutriuniv.domain.publicdata.dto.StagingLoadResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CSV 수신함 처리 이력 — 파이프라인이 /data/csv 에 둔 product_master_YYYYMMDD.csv 를 한 번 처리할 때마다 한 줄.
 * 같은 파일을 다시 처리하면(관리자 force · 실패 뒤 재시도) 줄이 더 생긴다. 파일의 상태는 그 파일의 마지막 줄.
 * DONE 줄이 있는 파일은 자동 실행이 다시 처리하지 않는다(중복 처리 방지).
 */
@Entity
@Table(name = "product_sync_inbox_files",
        indexes = @Index(name = "idx_product_sync_inbox_files_name", columnList = "file_name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSyncInboxFile {

    public enum Status { PROCESSING, DONE, FAILED }

    public enum Trigger { SCHEDULE, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    /** 파일 이름의 날짜(YYYYMMDD). */
    @Column(name = "file_date", nullable = false)
    private LocalDate fileDate;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_modified_at")
    private LocalDateTime fileModifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 10)
    private Trigger triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "fail_reason", length = 1000)
    private String failReason;

    /** 반영 단계의 적재 기록(product_loads) id. */
    @Column(name = "load_id")
    private Long loadId;

    @Column(name = "staged_count")
    private Integer stagedCount;

    /** 파일의 content_hash 와 서버 계산값이 다른 행 — 0 이 아니면 파이프라인과 서버 hash 규칙이 어긋난 것. */
    @Column(name = "hash_mismatch_count")
    private Integer hashMismatchCount;

    @Column(name = "created_count")
    private Integer createdCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    @Column(name = "unchanged_count")
    private Integer unchangedCount;

    @Column(name = "protected_count")
    private Integer protectedCount;

    @Column(name = "reactivated_count")
    private Integer reactivatedCount;

    @Column(name = "deactivated_count")
    private Integer deactivatedCount;

    @Column(name = "disappeared_count")
    private Integer disappearedCount;

    /** 어느 출처에서 사라진 제품이 10%를 넘어 비활성을 건너뛰었는가. */
    @Column(name = "deactivation_skipped")
    private Boolean deactivationSkipped;

    @Column(name = "fail_count")
    private Integer failCount;

    public static ProductSyncInboxFile start(String fileName, LocalDate fileDate, long fileSize,
                                             LocalDateTime fileModifiedAt, Trigger trigger) {
        ProductSyncInboxFile f = new ProductSyncInboxFile();
        f.fileName = fileName;
        f.fileDate = fileDate;
        f.fileSize = fileSize;
        f.fileModifiedAt = fileModifiedAt;
        f.triggeredBy = trigger;
        f.status = Status.PROCESSING;
        f.startedAt = LocalDateTime.now();
        return f;
    }

    public void done(StagingLoadResponse staged, SyncApplyResponse applied) {
        this.status = Status.DONE;
        this.finishedAt = LocalDateTime.now();
        this.stagedCount = staged.stagedCount();
        this.hashMismatchCount = staged.hashMismatchCount();
        this.loadId = applied.loadId();
        this.createdCount = applied.createdCount();
        this.updatedCount = applied.updatedCount();
        this.unchangedCount = applied.unchangedCount();
        this.protectedCount = applied.protectedCount();
        this.reactivatedCount = applied.reactivatedCount();
        this.deactivatedCount = applied.deactivatedCount();
        this.disappearedCount = applied.disappearedCount();
        this.deactivationSkipped = applied.deactivationSkipped();
        this.failCount = applied.failCount();
    }

    /** 복제 테이블은 채웠는데 반영에서 실패하면 staged 가 있다. */
    public void fail(String reason, StagingLoadResponse staged) {
        this.status = Status.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.failReason = reason == null ? "알 수 없는 오류" : reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        if (staged != null) {
            this.stagedCount = staged.stagedCount();
            this.hashMismatchCount = staged.hashMismatchCount();
        }
    }
}
