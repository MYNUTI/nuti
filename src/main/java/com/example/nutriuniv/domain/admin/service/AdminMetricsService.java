package com.example.nutriuniv.domain.admin.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.admin.dto.DataStatusResponse;
import com.example.nutriuniv.domain.admin.dto.RetentionMetricsResponse;
import com.example.nutriuniv.domain.admin.repository.AdminMetricsRepository;
import com.example.nutriuniv.domain.admin.repository.AdminMetricsRepository.CategoryRow;
import com.example.nutriuniv.domain.admin.repository.AdminMetricsRepository.DataTotals;
import com.example.nutriuniv.domain.admin.repository.AdminMetricsRepository.RetentionCounts;
import com.example.nutriuniv.domain.ranking.service.RankingGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 관리자 지표 (기능명세서 9.2·10.5).
 * 재방문: 집계 로직은 1차 그대로(세션 30분·유효방문·0~6/7~27일차), 2차 변경은 모수를 「동의한 사용자」로 재정의한 것 하나 — 변경 시점을 응답에 남긴다.
 */
@Service
public class AdminMetricsService {

    /** 랭킹 게이트 하한 — 판정 자체는 RankingGate(랭킹 배치와 같은 규칙). */
    public static final int RANKING_GATE_MIN_ANALYZED = RankingGate.MIN_ANALYZED;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminMetricsRepository repository;
    private final String definitionChangedAt;

    public AdminMetricsService(AdminMetricsRepository repository,
                               @Value("${app.retention.definition-changed-at:2026-09-28}") String definitionChangedAt) {
        this.repository = repository;
        this.definitionChangedAt = definitionChangedAt;
    }

    // ── GET /admin/metrics/retention ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RetentionMetricsResponse retention(String fromParam, String toParam) {
        LocalDate from = fromParam == null || fromParam.isBlank() ? LocalDate.of(2000, 1, 1) : parse(fromParam);
        LocalDate to   = toParam == null || toParam.isBlank() ? LocalDate.of(2100, 1, 1) : parse(toParam).plusDays(1);   // to 포함
        if (!from.isBefore(to)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "from 은 to 보다 이전이어야 합니다.");
        }
        // 날짜 경계는 KST → 서버 시간대의 LocalDateTime 으로
        ZoneId sys = ZoneId.systemDefault();
        LocalDateTime fromTs = from.atStartOfDay(KST).withZoneSameInstant(sys).toLocalDateTime();
        LocalDateTime toTs   = to.atStartOfDay(KST).withZoneSameInstant(sys).toLocalDateTime();

        RetentionCounts c = repository.retention(fromTs, toTs);
        double rate = c.baseCount() == 0 ? 0.0 : (double) c.revisitCount() / c.baseCount();

        return RetentionMetricsResponse.builder()
                .baseCount(c.baseCount())
                .revisitCount(c.revisitCount())
                .rate(Math.round(rate * 10000.0) / 10000.0)
                .verdict(verdict(rate))
                .definition(RetentionMetricsResponse.Definition.builder()
                        .sessionTimeoutMin(30)
                        .validVisit("한 세션에 검색 또는 제품 조회 1회 이상 (링크 클릭·필터 제외)")
                        .window("첫 방문일 0일차, 날짜가 바뀌면 +1 (KST). 0~6일차 방문자가 모수, 7~27일차 1회 이상이면 재방문")
                        .population("동의한 사용자 — 개인정보 동의로 익명 ID 를 발급받은 사람만. 둘러보고 나간 사용자는 모수에서 제외")
                        .changedAt(definitionChangedAt)
                        .build())
                .build();
    }

    /** PASS ≥ 20% · HOLD 7~20% · FAIL < 7% (기능명세서 9.2). */
    static String verdict(double rate) {
        if (rate >= 0.20) return "PASS";
        if (rate < 0.07) return "FAIL";
        return "HOLD";
    }

    // ── GET /admin/dashboard/data-status ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public DataStatusResponse dataStatus() {
        DataTotals t = repository.totals();
        List<CategoryRow> rows = repository.categoryAnalyzed();

        List<DataStatusResponse.CategoryRatio> categories = rows.stream()
                .map(r -> DataStatusResponse.CategoryRatio.builder()
                        .categoryId(r.categoryId())
                        .name(r.name())
                        .totalCount(r.totalCount())
                        .analyzedCount(r.analyzedCount())
                        .ratio(ratio(r.analyzedCount(), r.totalCount()))
                        .gradeACount(r.aCount())
                        .gradeDCount(r.dCount())
                        .rankingGatePassed(RankingGate.passes(r.analyzedCount(), r.aCount(), r.dCount()))
                        .build())
                .toList();

        return DataStatusResponse.builder()
                .totalProducts(t.totalProducts())
                .analyzedCount(t.analyzedCount())
                .analyzedRatio(ratio(t.analyzedCount(), t.totalProducts()))
                .barcodeCount(t.barcodeCount())
                .barcodeRatio(ratio(t.barcodeCount(), t.totalProducts()))
                .gradeDistribution(repository.gradeDistribution())
                .categoryAnalyzedRatios(categories)
                .lastLoadedAt(t.lastLoadedAt())
                .pendingRequestCount(repository.pendingAnalysisRequests())
                .build();
    }

    private static double ratio(long part, long total) {
        return total == 0 ? 0.0 : Math.round((double) part / total * 10000.0) / 10000.0;
    }

    private static LocalDate parse(String s) {
        try {
            return LocalDate.parse(s.trim(), DATE);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (yyyyMMdd)");
        }
    }
}
