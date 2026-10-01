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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 오류 제보 (기능명세서 10.4). 비로그인 가능·연락처 선택, 같은 사용자 하루 10건 초과 429 (KST, 소유자 → 세션 순).
 * 관리자가 DONE 으로 바꾸면(데이터를 고쳤다는 뜻) 제품에 재적재 보호를 켜고 수정 이력을 남긴다 — 실제 값 수정은 관리자 상품·영양성분 API.
 */
@Service
@RequiredArgsConstructor
public class ProductReportService {

    public static final int DAILY_LIMIT = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

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
        ReportType type = ReportType.from(request.getReportType());

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "content는 필수입니다.");
        }
        if (content.length() > ProductReport.MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "content는 " + ProductReport.MAX_CONTENT_LENGTH + "자 이하여야 합니다.");
        }
        String email = request.getContactEmail() == null || request.getContactEmail().isBlank() ? null : request.getContactEmail().trim();
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "contactEmail 형식이 올바르지 않습니다.");
        }

        Owner owner = ownerResolver.resolveOrNull(actor);
        enforceDailyLimit(owner, actor.sessionId());

        ProductReport saved = reportRepository.save(ProductReport.create(product.getId(), type, content, email, owner, actor.sessionId()));
        return new ReportCreateResponse(saved.getId(), saved.getStatus().name());
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
                        .contactEmail(r.getContactEmail())
                        .status(r.getStatus().name())
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
