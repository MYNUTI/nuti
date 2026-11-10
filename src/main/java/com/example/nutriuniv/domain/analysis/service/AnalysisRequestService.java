package com.example.nutriuniv.domain.analysis.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.dto.AdminAnalysisRequestPageResponse;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestCreateRequest;
import com.example.nutriuniv.domain.analysis.dto.AnalysisRequestResponse;
import com.example.nutriuniv.domain.analysis.dto.PopularRequestResponse;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 분석 요청 (v3 CTRB · ADM_002).
 * <ul>
 *   <li>바코드 · 검색어 · 제품 ID 는 식별값당 한 행 — INSERT … ON CONFLICT 로 request_count +1. 같은 기기(계정 · 기기 ID · 세션)는 한 번만 센다</li>
 *   <li>제품명 요청(이름 필수 · 브랜드 선택)은 늘 새 행</li>
 *   <li>자동 등록(스캔 404 · 영양정보 부족)은 조회 응답을 막지 않도록 별도 트랜잭션(REQUIRES_NEW)에서 조용히</li>
 *   <li>분석 완료 제품 409 ALREADY_ANALYZED. 검색어 · 제품명 요청은 합쳐서 하루 5건 초과 429 (KST)</li>
 * </ul>
 */
@Slf4j
@Service
public class AnalysisRequestService {

    public static final int DAILY_LIMIT = 5;
    public static final int KEYWORD_MAX_LENGTH = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<AnalysisRequestChannel> LIMITED = EnumSet.of(AnalysisRequestChannel.USER_KEYWORD, AnalysisRequestChannel.USER_NAME);

    static final String MSG_NEW = "요청을 받았어요. 분석이 끝나면 바로 볼 수 있어요.";
    static final String MSG_OTHERS_FIRST = "먼저 요청한 분이 있었어요. 분석이 끝나면 바로 볼 수 있어요.";
    static final String MSG_MINE_AGAIN = "이미 요청하신 제품이에요. 분석이 끝나면 바로 볼 수 있어요.";

    private static final String UPSERT_SQL = """
            INSERT INTO analysis_requests
                (type, barcode, keyword, product_id, request_count, status, first_requested_at, last_requested_at)
            VALUES (?, ?, ?, ?, 1, 'ANALYZING', now(), now())
            ON CONFLICT (%s) DO UPDATE
               SET request_count     = analysis_requests.request_count + 1,
                   last_requested_at = now()
            RETURNING id, (xmax = 0) AS inserted
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

    /** 접수 결과 — mine = 이 기기가 전에 이미 요청했다(이번엔 세지 않음), others = 다른 기기가 먼저 요청했다. */
    record Outcome(AnalysisRequest request, boolean mine, boolean others) {}

    // ── POST /analysis-requests (사용자 접수) ─────────────────────────────────────

    @Transactional
    public AnalysisRequestResponse submit(AnalysisRequestCreateRequest req, Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);          // 기기 ID 선택 — 한도 · 기여 집계용
        String sessionId = actor.sessionId();

        if (notBlank(req.getProductName())) {
            return toResponse(submitName(req, owner, sessionId));
        }

        AnalysisRequestType type = AnalysisRequestType.from(req.getType());
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
            throw new CustomException(ErrorCode.BAD_REQUEST, "NEW_PRODUCT는 barcode · keyword · productName 중 하나가 필수입니다.");
        }
        if (hasBarcode) {
            String barcode = BarcodeNormalizer.normalize(req.getBarcode());        // 형식 400 · 체크섬 400
            Product existing = productRepository.findByBarcode(barcode).stream().findFirst().orElse(null);
            if (existing != null) {
                requireNotAnalyzed(existing.getId());
                // 바코드는 있는데 영양정보가 부족한 제품 → 보강 요청으로 받는다
                return toResponse(upsertNutritionFill(existing.getId(), owner, sessionId, AnalysisRequestChannel.USER_PRODUCT));
            }
            return toResponse(upsertBarcode(barcode, owner, sessionId, AnalysisRequestChannel.USER_BARCODE));
        }
        String keyword = normalizeKeyword(req.getKeyword());
        enforceDailyLimit(owner, sessionId);
        return toResponse(upsertKeyword(keyword, owner, sessionId, AnalysisRequestChannel.USER_KEYWORD));
    }

    /** 제품명 요청 — 이름 필수(100자) · 브랜드 선택(50자). 늘 새 행. */
    private Outcome submitName(AnalysisRequestCreateRequest req, Owner owner, String sessionId) {
        String name = squash(req.getProductName());
        if (name.length() > AnalysisRequest.NAME_MAX_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "productName은 " + AnalysisRequest.NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        String brand = notBlank(req.getBrandName()) ? squash(req.getBrandName()) : null;
        if (brand != null && brand.length() > AnalysisRequest.BRAND_MAX_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "brandName은 " + AnalysisRequest.BRAND_MAX_LENGTH + "자 이하여야 합니다.");
        }
        String originBarcode = lenientBarcode(req.getOriginBarcode());
        String originKeyword = notBlank(req.getOriginKeyword()) ? truncate(squash(req.getOriginKeyword()), KEYWORD_MAX_LENGTH) : null;

        enforceDailyLimit(owner, sessionId);
        AnalysisRequest saved = requestRepository.save(AnalysisRequest.nameRequest(name, brand, originBarcode, originKeyword));
        eventRepository.save(AnalysisRequestEvent.create(saved.getId(), owner, sessionId, AnalysisRequestChannel.USER_NAME));
        return new Outcome(saved, false, false);
    }

    // ── 자동 등록 (조회 흐름에서 — 실패해도 응답을 막지 않는다) ──────────────────────

    /** 바코드 스캔했는데 데이터에 없음. */
    public void registerNewProductByBarcodeQuietly(String barcode, Actor actor) {
        quietly("NEW_PRODUCT barcode=" + barcode, () ->
                upsertBarcode(barcode, ownerResolver.resolveOrNull(actor), actor.sessionId(), AnalysisRequestChannel.AUTO_SCAN));
    }

    /** 영양정보 부족 제품 조회. */
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

    // ── GET /analysis-requests/popular (CTRB_006 많이 요청된 제품) ─────────────────

    @Transactional(readOnly = true)
    public PopularRequestResponse popular(int limit) {
        int n = Math.max(1, Math.min(limit, 30));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, r.product_name, r.brand_name, r.keyword, p.name AS fill_name, r.request_count, r.status
                  FROM analysis_requests r
                  LEFT JOIN products p ON p.id = r.product_id
                 WHERE r.status IN ('ANALYZING', 'REVIEWING')
                   AND (r.product_name IS NOT NULL OR r.keyword IS NOT NULL OR p.name IS NOT NULL)
                 ORDER BY r.request_count DESC, r.last_requested_at DESC
                 LIMIT ?
                """, n);
        List<PopularRequestResponse.Item> items = rows.stream().map(m -> {
            String name = (String) m.get("product_name");
            String brand = (String) m.get("brand_name");
            String display = name != null ? (brand == null ? name : brand + " " + name)
                    : m.get("keyword") != null ? (String) m.get("keyword") : (String) m.get("fill_name");
            AnalysisRequestStatus st = AnalysisRequestStatus.valueOf((String) m.get("status"));
            return new PopularRequestResponse.Item(((Number) m.get("id")).longValue(), display,
                    ((Number) m.get("request_count")).intValue(), st.name(), st.label());
        }).toList();
        return new PopularRequestResponse(items);
    }

    // ── 관리자 (ADM_002) ─────────────────────────────────────────────────────────

    /** kind: BARCODE | KEYWORD | NAME | PRODUCT — 요청 방식으로 거른다. */
    @Transactional(readOnly = true)
    public AdminAnalysisRequestPageResponse list(String typeParam, String kindParam, String statusParam, int page, int size) {
        if (page < 0 || size <= 0 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~100이어야 합니다.");
        }
        AnalysisRequestType type = notBlank(typeParam) ? AnalysisRequestType.from(typeParam) : null;
        AnalysisRequestStatus status = notBlank(statusParam) ? AnalysisRequestStatus.from(statusParam) : null;
        String kind = notBlank(kindParam) ? kindParam.trim().toUpperCase() : null;

        Specification<AnalysisRequest> spec = (root, query, cb) -> cb.conjunction();
        if (type != null)   spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (kind != null) {
            String column = switch (kind) {
                case "BARCODE" -> "barcode";
                case "KEYWORD" -> "keyword";
                case "NAME" -> "productName";
                case "PRODUCT" -> "productId";
                default -> throw new CustomException(ErrorCode.BAD_REQUEST, "kind는 BARCODE · KEYWORD · NAME · PRODUCT 중 하나여야 합니다.");
            };
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get(column)));
        }

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
                        .kind(r.kind())
                        .identifier(r.identifier())
                        .productId(r.getProductId())
                        .productName(r.isNameRequest() ? r.getProductName()
                                : r.getProductId() == null ? null : productNames.get(r.getProductId()))
                        .brandName(r.getBrandName())
                        .originBarcode(r.getOriginBarcode())
                        .originKeyword(r.getOriginKeyword())
                        .requestCount(r.getRequestCount())
                        .firstRequestedAt(r.getFirstRequestedAt())
                        .lastRequestedAt(r.getLastRequestedAt())
                        .status(r.getStatus().name())
                        .statusLabel(r.getStatus().label())
                        .processedAt(r.getProcessedAt())
                        .build())
                .toList();

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (AnalysisRequestStatus s : AnalysisRequestStatus.values()) statusCounts.put(s.name(), 0L);
        for (Object[] row : requestRepository.countGroupByStatus()) {
            statusCounts.put(((AnalysisRequestStatus) row[0]).name(), (Long) row[1]);
        }

        return AdminAnalysisRequestPageResponse.builder()
                .items(items)
                .statusCounts(statusCounts)
                .totalCount(result.getTotalElements())
                .page(page)
                .size(size)
                .build();
    }

    @Transactional
    public void updateStatus(Long requestId, String statusParam, Long adminUserId) {
        AnalysisRequestStatus next = AnalysisRequestStatus.from(statusParam);
        AnalysisRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 요청입니다."));
        request.changeStatus(next, adminUserId);
    }

    // ── upsert ────────────────────────────────────────────────────────────────────

    private Outcome upsertBarcode(String barcode, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        return upsert("barcode", barcode, AnalysisRequestType.NEW_PRODUCT, barcode, null, null, owner, sessionId, channel);
    }

    private Outcome upsertKeyword(String keyword, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        return upsert("keyword", keyword, AnalysisRequestType.NEW_PRODUCT, null, keyword, null, owner, sessionId, channel);
    }

    private Outcome upsertNutritionFill(Long productId, Owner owner, String sessionId, AnalysisRequestChannel channel) {
        return upsert("product_id", productId, AnalysisRequestType.NUTRITION_FILL, null, null, productId, owner, sessionId, channel);
    }

    /**
     * 식별값 컬럼 하나를 충돌 대상으로 INSERT … ON CONFLICT — 새 행이면 count 1, 있으면 +1.
     * 이 기기가 전에 이미 요청했으면 세지 않고 그대로 돌려준다(기기당 한 번).
     */
    private Outcome upsert(String column, Object value, AnalysisRequestType type, String barcode, String keyword, Long productId,
                           Owner owner, String sessionId, AnalysisRequestChannel channel) {
        Long existingId = jdbcTemplate.query("SELECT id FROM analysis_requests WHERE " + column + " = ?",
                rs -> rs.next() ? rs.getLong(1) : null, value);
        if (existingId != null && requestedBefore(existingId, owner, sessionId)) {
            return new Outcome(find(existingId), true, false);
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(String.format(UPSERT_SQL, column), type.name(), barcode, keyword, productId);
        Long id = ((Number) row.get("id")).longValue();
        boolean inserted = Boolean.TRUE.equals(row.get("inserted"));
        eventRepository.save(AnalysisRequestEvent.create(id, owner, sessionId, channel));
        return new Outcome(find(id), false, !inserted);
    }

    /** 같은 기기 판정 — 계정 → 기기 ID → 세션 순. 셋 다 없으면 알 수 없어 새 요청으로 센다. */
    private boolean requestedBefore(Long requestId, Owner owner, String sessionId) {
        if (owner != null && owner.isUser()) return eventRepository.existsByRequestIdAndUserId(requestId, owner.userId());
        if (owner != null) return eventRepository.existsByRequestIdAndAnonymousId(requestId, owner.anonymousId());
        if (notBlank(sessionId)) return eventRepository.existsByRequestIdAndSessionId(requestId, sessionId);
        return false;
    }

    private AnalysisRequest find(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("upsert 직후 요청을 찾을 수 없습니다: " + requestId));
    }

    // ── 검증 ─────────────────────────────────────────────────────────────────────

    private void requireNotAnalyzed(Long productId) {
        if (ProductStatus.of(productNutrientRepository.findById(productId).orElse(null)) == ProductStatus.ANALYZED) {
            throw new CustomException(ErrorCode.ALREADY_ANALYZED);
        }
    }

    /** 검색어 · 제품명 요청은 합쳐서 같은 사용자 하루 5건까지 (KST). 소유자가 없으면 세션 기준, 그것도 없으면 셀 수 없어 통과. */
    private void enforceDailyLimit(Owner owner, String sessionId) {
        LocalDateTime[] today = todayKst();
        long count;
        if (owner != null && owner.isUser()) {
            count = eventRepository.countByChannelInAndUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(LIMITED, owner.userId(), today[0], today[1]);
        } else if (owner != null) {
            count = eventRepository.countByChannelInAndAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(LIMITED, owner.anonymousId(), today[0], today[1]);
        } else if (notBlank(sessionId)) {
            count = eventRepository.countByChannelInAndSessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(LIMITED, sessionId, today[0], today[1]);
        } else {
            return;
        }
        if (count >= DAILY_LIMIT) {
            throw new CustomException(ErrorCode.DAILY_LIMIT_EXCEEDED, "분석 요청은 하루 " + DAILY_LIMIT + "건까지 보낼 수 있어요.");
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
        String k = squash(raw);
        if (k.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "keyword가 비어 있습니다.");
        }
        if (k.length() > KEYWORD_MAX_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "keyword는 " + KEYWORD_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return k;
    }

    /** 어디서 왔는지 남기는 값이라 형식이 틀려도 요청은 받는다(그 칸만 비움). */
    private static String lenientBarcode(String raw) {
        if (!notBlank(raw)) return null;
        try {
            return BarcodeNormalizer.normalize(raw);
        } catch (CustomException e) {
            return null;
        }
    }

    private static String squash(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static AnalysisRequestResponse toResponse(Outcome o) {
        AnalysisRequest r = o.request();
        return AnalysisRequestResponse.builder()
                .requestId(r.getId())
                .type(r.getType().name())
                .requestCount(r.getRequestCount())
                .status(r.getStatus().name())
                .statusLabel(r.getStatus().label())
                .alreadyRequested(o.mine() || o.others())
                .message(o.mine() ? MSG_MINE_AGAIN : o.others() ? MSG_OTHERS_FIRST : MSG_NEW)
                .build();
    }
}
