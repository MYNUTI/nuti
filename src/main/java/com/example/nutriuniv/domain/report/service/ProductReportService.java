package com.example.nutriuniv.domain.report.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductChangeLog;
import com.example.nutriuniv.domain.product.repository.ProductChangeLogRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import com.example.nutriuniv.domain.report.dto.AdminReportPageResponse;
import com.example.nutriuniv.domain.report.dto.AdminReportUpdateRequest;
import com.example.nutriuniv.domain.report.dto.ReportCreateRequest;
import com.example.nutriuniv.domain.report.dto.ReportCreateResponse;
import com.example.nutriuniv.domain.report.entity.ProductReport;
import com.example.nutriuniv.domain.report.entity.ReportStatus;
import com.example.nutriuniv.domain.report.entity.ReportType;
import com.example.nutriuniv.domain.report.repository.ProductReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 오류 제보 (v3 CTRB_008). 비로그인 가능, 연락처는 받지 않는다. 고른 항목마다 한 행, 같은 사람 · 제품 · 항목이 확인 중이면 새로 만들지 않는다.
 * 같은 사용자 하루 10건 초과 429 (KST, 소유자 → 세션 순).
 * 관리자가 DONE 으로 바꾸면(데이터를 고쳤다는 뜻) 제품에 재적재 보호를 켜고 수정 이력을 남긴다 — 실제 값 수정은 관리자 상품·영양성분 API.
 */
@Service
@RequiredArgsConstructor
public class ProductReportService {

    public static final int DAILY_LIMIT = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String MSG_RECEIVED = "보내 주셔서 고마워요. 확인 중이에요.";
    static final String MSG_DUPLICATE = "이미 보내 주신 내용이에요. 확인 중이에요.";

    private final ProductReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final ProductChangeLogRepository changeLogRepository;
    private final OwnerResolver ownerResolver;

    // ── POST /products/{productId}/reports ──────────────────────────────────────────

    @Transactional
    public ReportCreateResponse submit(Long productId, ReportCreateRequest request, Actor actor) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        List<String> raw = new ArrayList<>();
        if (request.getReportTypes() != null) raw.addAll(request.getReportTypes());
        if (request.getReportType() != null && !request.getReportType().isBlank()) raw.add(request.getReportType());
        if (raw.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "제보 항목을 하나 이상 골라 주세요.");
        }
        Set<ReportType> types = new LinkedHashSet<>();
        for (String r : raw) types.add(ReportType.from(r));

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (types.contains(ReportType.OTHER) && content.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "기타를 고르면 내용을 적어 주세요.");
        }
        if (content.length() > ProductReport.MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "content는 " + ProductReport.MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }
        String barcode = lenientBarcode(request.getBarcode());

        Owner owner = ownerResolver.resolveOrNull(actor);
        String sessionId = actor.sessionId();

        List<ReportType> fresh = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (ReportType t : types) {
            if (stillReviewing(owner, sessionId, product.getId(), t)) duplicates.add(t.name());
            else fresh.add(t);
        }
        if (fresh.isEmpty()) {
            return new ReportCreateResponse(List.of(), duplicates, true,
                    ReportStatus.RECEIVED.name(), ReportStatus.RECEIVED.label(), MSG_DUPLICATE);
        }

        enforceDailyLimit(owner, sessionId);
        List<Long> ids = new ArrayList<>();
        for (ReportType t : fresh) {
            ids.add(reportRepository.save(ProductReport.create(product.getId(), t, content, barcode, owner, sessionId)).getId());
        }
        return new ReportCreateResponse(ids, duplicates, false,
                ReportStatus.RECEIVED.name(), ReportStatus.RECEIVED.label(), MSG_RECEIVED);
    }

    /** 같은 사람(계정 → 기기 ID → 세션) · 같은 제품 · 같은 항목이 아직 확인 중인가. */
    private boolean stillReviewing(Owner owner, String sessionId, Long productId, ReportType type) {
        ReportStatus st = ReportStatus.RECEIVED;
        if (owner != null && owner.isUser()) return reportRepository.existsByUserIdAndProductIdAndReportTypeAndStatus(owner.userId(), productId, type, st);
        if (owner != null) return reportRepository.existsByAnonymousIdAndProductIdAndReportTypeAndStatus(owner.anonymousId(), productId, type, st);
        if (sessionId != null && !sessionId.isBlank()) return reportRepository.existsBySessionIdAndProductIdAndReportTypeAndStatus(sessionId, productId, type, st);
        return false;
    }

    private static String lenientBarcode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return BarcodeNormalizer.normalize(raw);
        } catch (CustomException e) {
            return null;
        }
    }

    private void enforceDailyLimit(Owner owner, String sessionId) {
        ZonedDateTime start = LocalDate.now(KST).atStartOfDay(KST);
        ZoneId sys = ZoneId.systemDefault();
        LocalDateTime from = start.withZoneSameInstant(sys).toLocalDateTime();
        LocalDateTime to = start.plusDays(1).withZoneSameInstant(sys).toLocalDateTime();

        long count;
        if (owner != null && owner.isUser()) {
            count = reportRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(owner.userId(), from, to);
        } else if (owner != null) {
            count = reportRepository.countByAnonymousIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(owner.anonymousId(), from, to);
        } else if (sessionId != null && !sessionId.isBlank()) {
            count = reportRepository.countBySessionIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(sessionId, from, to);
        } else {
            return;
        }
        if (count >= DAILY_LIMIT) {
            throw new CustomException(ErrorCode.DAILY_LIMIT_EXCEEDED, "오류 제보는 하루 " + DAILY_LIMIT + "건까지 접수할 수 있어요.");
        }
    }

    // ── GET /admin/reports ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminReportPageResponse list(String statusParam, int page, int size) {
        if (page < 0 || size <= 0 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~100이어야 합니다.");
        }
        ReportStatus status = statusParam == null || statusParam.isBlank() ? null : ReportStatus.from(statusParam);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductReport> result = status == null ? reportRepository.findAll(pageable) : reportRepository.findByStatus(status, pageable);

        Set<Long> productIds = result.getContent().stream().map(ProductReport::getProductId).collect(Collectors.toSet());
        Map<Long, String> names = productIds.isEmpty() ? Map.of()
                : productRepository.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Product::getName));

        List<AdminReportPageResponse.Item> items = result.getContent().stream()
                .map(r -> AdminReportPageResponse.Item.builder()
                        .reportId(r.getId())
                        .productId(r.getProductId())
                        .productName(names.get(r.getProductId()))
                        .reportType(r.getReportType().name())
                        .reportTypeLabel(r.getReportType().label())
                        .content(r.getContent())
                        .barcode(r.getBarcode())
                        .status(r.getStatus().name())
                        .statusLabel(r.getStatus().label())
                        .adminMemo(r.getAdminMemo())
                        .createdAt(r.getCreatedAt())
                        .handledAt(r.getHandledAt())
                        .build())
                .toList();

        return AdminReportPageResponse.builder()
                .items(items)
                .totalCount(result.getTotalElements())
                .page(page)
                .size(size)
                .build();
    }

    // ── PATCH /admin/reports/{reportId} ─────────────────────────────────────────────

    @Transactional
    public void update(Long reportId, AdminReportUpdateRequest request, Long adminUserId) {
        ProductReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 제보입니다."));
        boolean hasStatus = request.getStatus() != null && !request.getStatus().isBlank();
        boolean hasMemo = request.getMemo() != null;
        if (!hasStatus && !hasMemo) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "status 또는 memo 중 하나는 있어야 합니다.");
        }
        String memo = hasMemo ? request.getMemo().trim() : null;
        if (memo != null && memo.length() > ProductReport.MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "memo는 " + ProductReport.MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }
        ReportStatus next = hasStatus ? ReportStatus.from(request.getStatus()) : null;
        ReportStatus before = report.getStatus();
        report.handle(next, memo, adminUserId);

        // DONE = 데이터를 고쳤다 → 재적재 보호 + 수정 이력 (10.4). 기타(OTHER)는 데이터 수정이 아니므로 보호를 켜지 않는다
        if (next == ReportStatus.DONE && before != ReportStatus.DONE && report.getReportType().correctsData()) {
            productRepository.findById(report.getProductId()).ifPresent(Product::markManuallyCorrected);
            String summary = "제보 #" + report.getId() + " 처리 완료 (" + report.getReportType().label() + ")"
                    + (memo == null || memo.isEmpty() ? "" : " — " + memo);
            changeLogRepository.save(ProductChangeLog.create(report.getProductId(), report.getId(), adminUserId, ProductChangeLog.Source.REPORT_DONE, summary));
        }
    }
}
