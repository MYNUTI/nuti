package com.example.nutriuniv.domain.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /admin/product-sync/inbox — 수신함 폴더(/data/csv)의 파일과 파일별 처리 상태.
 * <ul>
 *   <li>PENDING: 아직 처리하지 않은 최신 파일 (다음 자동 실행 · 수동 실행 대상)</li>
 *   <li>PROCESSING · DONE · FAILED: 그 파일의 마지막 처리 결과</li>
 *   <li>SUPERSEDED: 처리한 적 없는데 더 새 파일이 있어 건너뛴 파일 (파일마다 전체 데이터라 최신 하나만 반영한다)</li>
 * </ul>
 */
public record InboxListResponse(
        String dir,
        boolean dirExists,
        boolean running,             // 지금 처리 중인 파일이 있는가
        List<Item> files,            // product_master_YYYYMMDD.csv, 최신 날짜 먼저
        List<String> ignoredFiles    // 이름 규칙에 맞지 않아 읽지 않는 파일 (.tmp 등)
) {

    public enum FileStatus { PENDING, PROCESSING, DONE, FAILED, SUPERSEDED }

    public record Item(
            String fileName,
            LocalDate fileDate,
            long sizeBytes,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime modifiedAt,
            FileStatus status,
            int attemptCount,                 // 처리한 횟수
            String triggeredBy,               // 마지막 처리: SCHEDULE · MANUAL
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startedAt,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime finishedAt,
            String failReason,
            Result result                     // 마지막 처리의 건수 (처리 전이면 null)
    ) {}

    public record Result(
            Integer stagedCount,
            Integer hashMismatchCount,
            Integer createdCount,
            Integer updatedCount,
            Integer unchangedCount,
            Integer protectedCount,
            Integer reactivatedCount,
            Integer deactivatedCount,
            Integer disappearedCount,
            Boolean deactivationSkipped,
            Integer failCount
    ) {}
}
