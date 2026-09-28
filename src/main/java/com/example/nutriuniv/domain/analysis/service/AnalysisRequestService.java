package com.example.nutriuniv.domain.analysis.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.dto.AdminAnalysisRequestPageResponse;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestCreateRequest;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestResponse;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequest;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestEvent;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestStatus;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestType;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 분석 대기 목록 (기능명세서 10.2).
 * <ul>
 *   <li>같은 식별값(바코드·검색어·제품 ID)은 한 행 — INSERT … ON CONFLICT 로 request_count +1 (동시 요청에도 행이 갈라지지 않음)</li>
 *   <li>자동 등록(스캔 404·영양정보 부족)은 조회 응답을 막지 않도록 별도 트랜잭션(REQUIRES_NEW)에서 조용히</li>
 *   <li>사용자 접수: 분석 완료 제품 409 ALREADY_ANALYZED, 검색어 접수 하루 5건 초과 429 (KST 기준, 소유자 없으면 세션 기준)</li>
 * </ul>
 */
@Slf4j
@Service
public class AnalysisRequestService {

    public static final int KEYWORD_DAILY_LIMIT = 5;
    public static final int KEYWORD_MAX_LENGTH = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String UPSERT_SQL = """
            INSERT INTO analysis_requests
                (type, barcode, keyword, product_id, request_count, status, first_requested_at, last_requested_at)
            VALUES (?, ?, ?, ?, 1, 'WAITING', now(), now())
            ON CONFLICT (%s) DO UPDATE
               SET request_count     = analysis_requests.request_count + 1,
                   last_requested_at = now()
            RETURNING id
            """;

    private final AnalysisRequestRepository requestRepository;
    private final AnalysisRequestEventRepository eventRepository;
    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final OwnerResolver ownerResolver;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txNew;

    public AnalysisRequestService(AnalysisRequestRepository requestRepository,
                                  AnalysisRequestEventRepository eventRepository,
                                  ProductRepository productRepository,
                                  ProductNutrientRepository productNutrientRepository,
                                  OwnerResolver ownerResolver,
                                  JdbcTemplate jdbcTemplate,
                                  PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.productRepository = productRepository;
        this.productNutrientRepository = productNutrientRepository;
        this.ownerResolver = ownerResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── POST /analysis-requests (사용자 접수) ─────────────────────────────────────

    @Transactional
    public AnalysisRequestResponse submit(AnalysisRequestCreateRequest req, Actor actor) {
        AnalysisRequestType type = AnalysisRequestType.from(req.getType());
        Owner owner = ownerResolver.resolveOrNull(actor);          // 익명 ID 선택 — 한도·기여 집계용
        String sessionId = actor.sessionId();

        if (type == AnalysisRequestType.NUTRITION_FILL) {
            if (req.getProductId() == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "NUTRITION_FILL은 productId가 필수입니다.");
            }
            Product product = productRepository.findById(req.getProductId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
            requireNotAnalyzed(product.getId());
            return toResponse(upsertNutritionFill(product.getId(), owner, sessionId, AnalysisRequestChannel.USER_PRODUCT));
        }

        boolean hasBarcode = notBlank(req.getBarcode());
        boolean hasKeyword = notBlank(req.getKeyword());
        if (!hasBarcode && !hasKeyword) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "NEW_PRODUCT는 barcode 또는 keyword가 필수입니다.");
        }
        if (hasBarcode) {
            String barcode = BarcodeNormalizer.normalize(req.getBarcode());        // 형식 400 · 체크섬 400
            Product existing = productRepository.findByBarcode(barcode).orElse(null);
            if (existing != null) {
                requireNotAnalyzed(existing.getId());
                // 바코드는 있는데 영양정보가 부족한 제품 → 보강 요청으로 받는다 (신규 등록 요청으로 두면 관리자가 같은 제품을 다시 만든다)
                return toResponse(upsertNutritionFill(existing.getId(), owner, sessionId, AnalysisRequestChannel.USER_PRODUCT));
            }
            return toResponse(upsertBarcode(barcode, owner, sessionId, AnalysisRequestChannel.USER_BARCODE));
        }
        String keyword = normalizeKeyword(req.getKeyword());
        enforceKeywordDailyLimit(owner, sessionId);
        return toResponse(upsertKeyword(keyword, owner, sessionId, AnalysisRequestChannel.USER_KEYWORD));
    }

    // ── 자동 등록 (조회 흐름에서 — 실패해도 응답을 막지 않는다) ──────────────────────

    /** 바코드 스캔했는데 데이터에 없음 (3.2). */
    public void registerNewProductByBarcodeQuietly(String barcode, Actor actor) {
        quietly("NEW_PRODUCT barcode=" + barcode, () ->
                upsertBarcode(barcode, ownerResolver.resolveOrNull(actor), actor.sessionId(), AnalysisRequestChannel.AUTO_SCAN));
    }

    /** 영양정보 부족 제품 조회 (2.3·5.1·3.2). */
    public void registerNutritionFillQuietly(Long productId, Actor actor, AnalysisRequestChannel channel) {
        quietly("NUTRITION_FILL productId=" + productId, () ->
                upsertNutritionFill(productId, ownerResolver.resolveOrNull(actor), actor.sessionId(), channel));
    }

    private void quietly(String what, Runnable work) {
        try {
            txNew.executeWithoutResult(status -> work.run());
        } catch (Exception e) {
            log.warn("[ANALYSIS] 자동 등록 실패 ({}) — {}", what, e.getMessage());
        }
    }

    // ── 관리자 (10.2) ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminAnalysisRequestPageResponse list(String typeParam, String statusParam, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM);
        }
        AnalysisRequestType type = notBlank(typeParam) ? AnalysisRequestType.from(typeParam) : null;
        AnalysisRequestStatus status = notBlank(statusParam) ? AnalysisRequestStatus.from(statusParam) : null;

        Specification<AnalysisRequest> spec = (root, query, cb) -> cb.conjunction();
        if (type != null)   spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));

        Sort sort = Sort.by(Sort.Direction.DESC, "requestCount").and(Sort.by(Sort.Direction.DESC, "lastRequestedAt"));
        Page<AnalysisRequest> result = requestRepository.findAll(spec, PageRequest.of(page, size, sort));

        Set<Long> productIds = result.getContent().stream()
                .map(AnalysisRequest::getProductId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> productNames = productIds.isEmpty() ? Map.of()
                : productRepository.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Product::getName));

        List<AdminAnalysisRequestPageResponse.Item> items = result.getContent().stream()
                .map(r -> AdminAnalysisRequestPageResponse.Item.builder()
                        .requestId(r.getId())
                        .type(r.getType().name())
                        .identifier(r.identifier())
                        .productId(r.getProductId())
                        .productName(r.getProductId() == null ? null : productNames.get(r.getProductId()))
                        .requestCount(r.getRequestCount())
                        .firstRequestedAt(r.getFirstRequestedAt())
                        .lastRequestedAt(r.getLastRequestedAt())
                        .status(r.getStatus().name())
                        .processedAt(r.getProcessedAt())
                        .build())
                .toList();

        return AdminAnalysisRequestPageResponse.builder()
                .items(items)
                .totalCount(result.getTotalElements())
                .page(page)
                .size(size)
                .build();
    }

    @Transactional
    public void updateStatus(Long requestId, String statusParam, Long adminUserId) {
        AnalysisRequestStatus next = AnalysisRequestStatus.from(statusParam);
        AnalysisRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 대기 항목입니다."));
        request.changeStatus(next, adminUserId);
    }

    // ── upsert ────────────────────────────────────────────────────────────────────

    private AnalysisRequest upsertBarcode(String barcode, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        Long id = upsert("barcode", AnalysisRequestType.NEW_PRODUCT, barcode, null, null);
        return recordEvent(id, owner, sessionId, channel);
    }

    private AnalysisRequest upsertKeyword(String keyword, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        Long id = upsert("keyword", AnalysisRequestType.NEW_PRODUCT, null, keyword, null);
        return recordEvent(id, owner, sessionId, channel);
    }

    private AnalysisRequest upsertNutritionFill(Long productId, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        Long id = upsert("product_id", AnalysisRequestType.NUTRITION_FILL, null, null, productId);
        return recordEvent(id, owner, sessionId, channel);
    }

    /** 식별값 컬럼 하나를 충돌 대상으로 INSERT … ON CONFLICT — 새 행이면 count 1, 있으면 +1. */
    private Long upsert(String conflictColumn, AnalysisRequestType type, String barcode, String keyword, Long productId) {
        String sql = String.format(UPSERT_SQL, conflictColumn);
        return jdbcTemplate.queryForObject(sql, Long.class, type.name(), barcode, keyword, productId);
    }

    private AnalysisRequest recordEvent(Long requestId, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        eventRepository.save(AnalysisRequestEvent.create(requestId, owner, sessionId, channel));
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("upsert 직후 대기 항목을 찾을 수 없습니다: " + requestId));
    }

    // ── 검증 ─────────────────────────────────────────────────────────────────────

    private void requireNotAnalyzed(Long productId) {
        if (ProductStatus.of(productNutrientRepository.findById(productId).orElse(null)) == ProductStatus.ANALYZED) {
            throw new CustomException(ErrorCode.ALREADY_ANALYZED);
        }
    }

    /** 검색어 접수는 같은 사용자 하루 5건까지 (KST). 소유자가 없으면 세션 기준, 그것도 없으면 셀 수 없어 통과. */
    private void enforceKeywordDailyLimit(Owner owner, String sessionId) {
        LocalDateTime[] today = todayKst();
        AnalysisRequestChannel ch = AnalysisRequestChannel.USER_KEYWORD;
        long count;
        if (owner != null && owner.isUser()) {
            count = eventRepository.countByChannelAndUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ch, owner.userId(), today[0], today[1]);
        } else if (owner != null) {
            count = eventRepository.countByChannelAndAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ch, owner.anonymousId(), today[0], today[1]);
        } else if (notBlank(sessionId)) {
            count = eventRepository.countByChannelAndSessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ch, sessionId, today[0], today[1]);
        } else {
            return;
        }
        if (count >= KEYWORD_DAILY_LIMIT) {
            throw new CustomException(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "찾는 제품 알려주기는 하루 " + KEYWORD_DAILY_LIMIT + "건까지 접수할 수 있어요.");
        }
    }

    /** KST 오늘 [00:00, 다음날 00:00) 을 서버 시간대의 LocalDateTime 으로. */
    private static LocalDateTime[] todayKst() {
        ZonedDateTime start = LocalDate.now(KST).atStartOfDay(KST);
        ZoneId sys = ZoneId.systemDefault();
        return new LocalDateTime[]{
                start.withZoneSameInstant(sys).toLocalDateTime(),
                start.plusDays(1).withZoneSameInstant(sys).toLocalDateTime()
        };
    }

    static String normalizeKeyword(String raw) {
        String k = raw.trim().replaceAll("\\s+", " ");
        if (k.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "keyword가 비어 있습니다.");
        }
        if (k.length() > KEYWORD_MAX_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "keyword는 " + KEYWORD_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return k;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static AnalysisRequestResponse toResponse(AnalysisRequest r) {
        return AnalysisRequestResponse.builder()
                .requestId(r.getId())
                .type(r.getType().name())
                .requestCount(r.getRequestCount())
                .status(r.getStatus().name())
                .build();
    }
}
