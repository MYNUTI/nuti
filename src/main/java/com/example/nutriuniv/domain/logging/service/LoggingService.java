package com.example.nutriuniv.domain.logging.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.logging.dto.CtaLogRequest;
import com.example.nutriuniv.domain.logging.dto.FilterLogRequest;
import com.example.nutriuniv.domain.logging.dto.ImpressionLogRequest;
import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.logging.dto.SearchLogRequest;
import com.example.nutriuniv.domain.logging.dto.OnboardingStepLogRequest;
import com.example.nutriuniv.domain.logging.dto.ScanEventLogRequest;
import com.example.nutriuniv.domain.logging.dto.ViewLogRequest;
import com.example.nutriuniv.domain.logging.entity.OnboardingStepLog;
import com.example.nutriuniv.domain.logging.entity.ProductCtaLog;
import com.example.nutriuniv.domain.logging.entity.ProductFilterLog;
import com.example.nutriuniv.domain.logging.entity.ProductViewLog;
import com.example.nutriuniv.domain.logging.entity.ScanEventLog;
import com.example.nutriuniv.domain.logging.entity.ScanEventResult;
import com.example.nutriuniv.domain.logging.entity.SearchLog;
import com.example.nutriuniv.domain.logging.repository.OnboardingStepLogRepository;
import com.example.nutriuniv.domain.logging.repository.ProductCtaLogRepository;
import com.example.nutriuniv.domain.logging.repository.ProductFilterLogRepository;
import com.example.nutriuniv.domain.logging.repository.ProductViewLogRepository;
import com.example.nutriuniv.domain.logging.repository.ScanEventLogRepository;
import com.example.nutriuniv.domain.logging.repository.SearchLogRepository;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final OnboardingStepLogRepository onboardingStepLogRepository;
    private final ProductRepository productRepository;
    private final VisitTrackingService visitTrackingService;
    private final EntityManager em;

    // ── POST /logging/view (product_detail_viewed, 유효 방문) ──────────────────────

    @Transactional
    public void logProductView(ViewLogRequest request, LogContext ctx) {
        if (request.getProductId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "product_id는 필수입니다.");
        }

        // 유효 방문 → 세션 추적 (상품 존재 여부와 무관하게 방문 자체는 카운트)
        visitTrackingService.track(ctx, true);

        try {
            Product product = productRepository.findById(request.getProductId()).orElse(null);
            if (product == null) {
                log.warn("[ViewLog] 존재하지 않는 product_id: {}", request.getProductId());
                return;
            }
            productViewLogRepository.save(ProductViewLog.create(product, ctx));
        } catch (Exception e) {
            log.error("[ViewLog] 로그 저장 실패 - productId: {}, error: {}", request.getProductId(), e.getMessage());
        }
    }

    // ── POST /logging/cta (coupang_outbound_clicked, 구매전환 관찰) ────────────────

    @Transactional
    public void logProductCta(CtaLogRequest request, LogContext ctx) {
        if (request.getProductId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "product_id는 필수입니다.");
        }

        // cta는 유효 방문 아님 → 세션 종료시각만 갱신
        visitTrackingService.track(ctx, false);

        try {
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
        } catch (Exception e) {
            log.error("[CtaLog] 로그 저장 실패 - productId: {}, error: {}", request.getProductId(), e.getMessage());
        }
    }

    // ── POST /logging/search (search_performed, 유효 방문) ─────────────────────────

    @Transactional
    public void logSearch(SearchLogRequest request, LogContext ctx) {
        if (request.getKeyword() == null || request.getKeyword().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "keyword는 필수입니다.");
        }

        // 유효 방문 → 세션 추적
        visitTrackingService.track(ctx, true);

        try {
            searchLogRepository.save(SearchLog.create(ctx, request.getKeyword().trim(), request.getResultCount()));
        } catch (Exception e) {
            log.error("[SearchLog] 로그 저장 실패 - keyword: {}, error: {}", request.getKeyword(), e.getMessage());
        }
    }

    // ── POST /logging/filter (compare_or_filter_used, 핵심 행동) ───────────────────

    @Transactional
    public void logFilter(FilterLogRequest request, LogContext ctx) {
        if (request.getFilterType() == null || request.getFilterType().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "filter_type은 필수입니다.");
        }

        // filter는 유효 방문 아님 → 세션 종료시각만 갱신
        visitTrackingService.track(ctx, false);

        try {
            productFilterLogRepository.save(
                    ProductFilterLog.create(ctx, request.getFilterType().trim(), request.getFilterValue()));
        } catch (Exception e) {
            log.error("[FilterLog] 로그 저장 실패 - filterType: {}, error: {}",
                    request.getFilterType(), e.getMessage());
        }
    }

    // ── POST /logging/scan-event (스캔 실패 원인별 적재, 기능명세서 9.1 신규) ────────

    /**
     * 인식 실패 / 데이터에 없음 / 체크섬 실패 / 권한 거부를 구분해 쌓는다 — 원인이 달라 화면도 로그도 분리(3.2).
     * 유효 방문이 아니므로 세션 종료시각만 갱신. 바코드는 인식이 된 경우(NOT_IN_DATA·CHECKSUM_FAIL)에만 남긴다. 저장 실패해도 200.
     */
    @Transactional
    public void logScanEvent(ScanEventLogRequest request, LogContext ctx) {
        ScanEventResult result = ScanEventResult.from(request.getResult());   // 허용값 외 400

        visitTrackingService.track(ctx, false);

        try {
            String barcode = result.keepsBarcode() && request.getBarcode() != null && !request.getBarcode().isBlank()
                    ? truncate(request.getBarcode().trim(), 14) : null;
            String surface = request.getSurface() == null || request.getSurface().isBlank()
                    ? null : truncate(request.getSurface().trim(), 20);
            scanEventLogRepository.save(ScanEventLog.create(ctx, result, barcode, surface));
        } catch (Exception e) {
            log.error("[ScanEventLog] 로그 저장 실패 - result: {}, error: {}", request.getResult(), e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ── POST /logging/onboarding-step (온보딩 단계 이벤트) ────────────────────────────

    /** 허용값 외 400, 저장 실패는 200. 유효 방문·세션 추적과 무관(3회 이탈 카운트는 클라 localStorage). */
    @Transactional
    public void logOnboardingStep(OnboardingStepLogRequest request, LogContext ctx) {
        String step = request.normalizedStep();
        String action = request.normalizedAction();
        String reason = request.normalizedReason();
        try {
            onboardingStepLogRepository.save(OnboardingStepLog.create(ctx, step, action, reason));
        } catch (Exception e) {
            log.error("[OnboardingStepLog] 로그 저장 실패 - step: {}, action: {}, error: {}", step, action, e.getMessage());
        }
    }

    // ── POST /logging/impression (상품 노출, 음성 샘플용) ──────────────────────────

    /**
     * 한 화면에 보인 상품들을 벌크 insert. 세션 내 (product_id, surface) 중복은
     * 유니크 제약으로 무시(ON CONFLICT DO NOTHING). 노출은 유효 방문이 아니므로
     * 세션 추적(visitTrackingService)은 호출하지 않는다.
     */
    @Transactional
    public void logImpressions(ImpressionLogRequest request, LogContext ctx) {
        if (request.getSurface() == null || request.getSurface().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "surface는 필수입니다.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        try {
            // 요청 내 동일 productId 제거 (첫 등장 순서 유지)
            Map<Long, ImpressionLogRequest.Item> unique = new LinkedHashMap<>();
            for (ImpressionLogRequest.Item it : request.getItems()) {
                if (it.getProductId() != null) {
                    unique.putIfAbsent(it.getProductId(), it);
                }
            }
            if (unique.isEmpty()) {
                return;
            }

            StringBuilder sql = new StringBuilder("""
                    INSERT INTO product_impression_logs
                      (user_id, anonymous_id, session_id, cohort, product_id, surface, position, keyword, created_at)
                    VALUES """);
            List<Object> params = new ArrayList<>();
            int idx = 0;
            for (ImpressionLogRequest.Item it : unique.values()) {
                if (idx++ > 0) sql.append(",");
                sql.append("(?,?,?,?,?,?,?,?, now())");
                params.add(ctx.userId());
                params.add(ctx.anonymousId());
                params.add(ctx.sessionId());
                params.add(ctx.cohort());
                params.add(it.getProductId());
                params.add(request.getSurface());
                params.add(it.getPosition());
                params.add(request.getKeyword());
            }
            sql.append(" ON CONFLICT (session_id, product_id, surface) DO NOTHING");

            var query = em.createNativeQuery(sql.toString());
            for (int p = 0; p < params.size(); p++) {
                query.setParameter(p + 1, params.get(p));
            }
            query.executeUpdate();
        } catch (Exception e) {
            log.error("[ImpressionLog] 로그 저장 실패 - surface: {}, error: {}",
                    request.getSurface(), e.getMessage());
        }
    }
}
