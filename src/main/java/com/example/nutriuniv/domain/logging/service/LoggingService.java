package com.example.nutriuniv.domain.logging.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.logging.dto.CtaLogRequest;
import com.example.nutriuniv.domain.logging.dto.FilterLogRequest;
import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.logging.dto.SearchLogRequest;
import com.example.nutriuniv.domain.logging.dto.ScanEventLogRequest;
import com.example.nutriuniv.domain.logging.dto.ViewLogRequest;
import com.example.nutriuniv.domain.logging.entity.ProductCtaLog;
import com.example.nutriuniv.domain.logging.entity.ProductFilterLog;
import com.example.nutriuniv.domain.logging.entity.ProductViewLog;
import com.example.nutriuniv.domain.logging.entity.ScanEventLog;
import com.example.nutriuniv.domain.logging.entity.ScanEventResult;
import com.example.nutriuniv.domain.logging.entity.SearchLog;
import com.example.nutriuniv.domain.logging.repository.ProductCtaLogRepository;
import com.example.nutriuniv.domain.logging.repository.ProductFilterLogRepository;
import com.example.nutriuniv.domain.logging.repository.ProductViewLogRepository;
import com.example.nutriuniv.domain.logging.repository.ScanEventLogRepository;
import com.example.nutriuniv.domain.logging.repository.SearchLogRepository;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 행동 로그 적재. 입력 검증(400)만 요청 흐름에서 하고, 저장은 전부 LogWriter(별도 트랜잭션)로 넘긴다 —
 * 저장이 실패해도 200 이고, 부른 쪽(스캔 등)의 트랜잭션을 망가뜨리지 않는다.
 * 세션 추적과 이벤트 저장은 따로 감싸서 한쪽이 실패해도 다른 쪽은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingService {

    private static final int CTA_RATE_LIMIT_HOURS = 1;

    private final ProductViewLogRepository productViewLogRepository;
    private final ProductCtaLogRepository productCtaLogRepository;
    private final SearchLogRepository searchLogRepository;
    private final ProductFilterLogRepository productFilterLogRepository;
    private final ScanEventLogRepository scanEventLogRepository;
    private final ProductRepository productRepository;
    private final VisitTrackingService visitTrackingService;
    private final LogWriter logWriter;

    // ── POST /logging/view (product_detail_viewed, 유효 방문) ──────────────────────

    public void logProductView(ViewLogRequest request, LogContext ctx) {
        if (request.getProductId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "product_id는 필수입니다.");
        }

        // 유효 방문 → 세션 추적 (상품 존재 여부와 무관하게 방문 자체는 카운트)
        track(ctx, true);

        logWriter.write("ViewLog", () -> {
            Product product = productRepository.findById(request.getProductId()).orElse(null);
            if (product == null) {
                log.warn("[ViewLog] 존재하지 않는 product_id: {}", request.getProductId());
                return;
            }
            productViewLogRepository.save(ProductViewLog.create(product, ctx));
        });
    }

    // ── POST /logging/cta (coupang_outbound_clicked, 구매전환 관찰) ────────────────

    public void logProductCta(CtaLogRequest request, LogContext ctx) {
        if (request.getProductId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "product_id는 필수입니다.");
        }

        // cta는 유효 방문 아님 → 세션 종료시각만 갱신
        track(ctx, false);

        logWriter.write("CtaLog", () -> {
            Product product = productRepository.findById(request.getProductId()).orElse(null);
            if (product == null) {
                log.warn("[CtaLog] 존재하지 않는 product_id: {}", request.getProductId());
                return;
            }

            // 로그인 유저는 1시간 내 동일 상품 중복 클릭 무시
            if (ctx.userId() != null) {
                LocalDateTime threshold = LocalDateTime.now().minusHours(CTA_RATE_LIMIT_HOURS);
                boolean alreadyLogged = productCtaLogRepository
                        .existsByUserIdAndProduct_IdAndCreatedAtAfter(ctx.userId(), request.getProductId(), threshold);
                if (alreadyLogged) {
                    log.debug("[CtaLog] 중복 클릭 무시 - userId: {}, productId: {}", ctx.userId(), request.getProductId());
                    return;
                }
            }

            productCtaLogRepository.save(ProductCtaLog.create(product, ctx));
        });
    }

    // ── POST /logging/search (search_performed, 유효 방문) ─────────────────────────

    public void logSearch(SearchLogRequest request, LogContext ctx) {
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "keyword는 필수입니다.");
        }

        // 유효 방문 → 세션 추적
        track(ctx, true);

        String keyword = truncate(request.getKeyword().trim(), 30);          // search_logs.keyword 길이
        logWriter.write("SearchLog", () ->
                searchLogRepository.save(SearchLog.create(ctx, keyword, request.getResultCount())));
    }

    // ── POST /logging/filter (compare_or_filter_used, 핵심 행동) ───────────────────

    public void logFilter(FilterLogRequest request, LogContext ctx) {
        if (request.getFilterType() == null || request.getFilterType().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "filter_type은 필수입니다.");
        }

        // filter는 유효 방문 아님 → 세션 종료시각만 갱신
        track(ctx, false);

        String filterType = truncate(request.getFilterType().trim(), 30);
        String filterValue = request.getFilterValue() == null ? null : truncate(request.getFilterValue(), 100);
        logWriter.write("FilterLog", () ->
                productFilterLogRepository.save(ProductFilterLog.create(ctx, filterType, filterValue)));
    }

    // ── POST /logging/scan-event (스캔 실패 원인별 적재, 기능명세서 9.1 신규) ────────

    /**
     * 인식 실패 / 데이터에 없음 / 체크섬 실패 / 권한 거부를 구분해 쌓는다 — 원인이 달라 화면도 로그도 분리(3.2).
     * 유효 방문이 아니므로 세션 종료시각만 갱신. 바코드는 인식이 된 경우(NOT_IN_DATA·CHECKSUM_FAIL)에만 남긴다. 저장 실패해도 200.
     */
    public void logScanEvent(ScanEventLogRequest request, LogContext ctx) {
        ScanEventResult result = ScanEventResult.from(request.getResult());   // 허용값 외 400

        track(ctx, false);

        String barcode = result.keepsBarcode() && request.getBarcode() != null && !request.getBarcode().isBlank()
                ? truncate(request.getBarcode().trim(), 14) : null;
        String surface = request.getSurface() == null || request.getSurface().isBlank()
                ? null : truncate(request.getSurface().trim(), 20);
        logWriter.write("ScanEventLog", () ->
                scanEventLogRepository.save(ScanEventLog.create(ctx, result, barcode, surface)));
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    private void track(LogContext ctx, boolean qualifying) {
        logWriter.write("VisitTracking", () -> visitTrackingService.track(ctx, qualifying));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
