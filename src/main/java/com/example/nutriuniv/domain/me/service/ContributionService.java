package com.example.nutriuniv.domain.me.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequest;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestStatus;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.me.dto.ContributionsResponse;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.report.entity.ProductReport;
import com.example.nutriuniv.domain.report.entity.ReportStatus;
import com.example.nutriuniv.domain.report.repository.ProductReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 내 기여 (v3 CTRB_006) — 내가 직접 보낸 분석 요청(자동 등록 제외) · 제보와 상태. 계정 → 기기 ID 순, 둘 다 없으면 빈 목록.
 */
@Service
@RequiredArgsConstructor
public class ContributionService {

    private static final Set<AnalysisRequestChannel> USER_CHANNELS = EnumSet.of(
            AnalysisRequestChannel.USER_BARCODE, AnalysisRequestChannel.USER_KEYWORD,
            AnalysisRequestChannel.USER_PRODUCT, AnalysisRequestChannel.USER_NAME);

    private final OwnerResolver ownerResolver;
    private final AnalysisRequestEventRepository eventRepository;
    private final AnalysisRequestRepository requestRepository;
    private final ProductReportRepository reportRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ContributionsResponse list(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        if (owner == null) {
            return new ContributionsResponse(new ContributionsResponse.Summary(0, 0, 0, 0, 0, 0, 0, 0), List.of(), List.of());
        }

        List<Object[]> mine = owner.isUser()
                ? eventRepository.findMyRequestsByUserId(owner.userId(), USER_CHANNELS)
                : eventRepository.findMyRequestsByAnonymousId(owner.anonymousId(), USER_CHANNELS);
        Map<Long, AnalysisRequest> requests = requestRepository.findAllById(mine.stream().map(r -> (Long) r[0]).toList())
                .stream().collect(Collectors.toMap(AnalysisRequest::getId, Function.identity()));

        List<ProductReport> reports = owner.isUser()
                ? reportRepository.findByUserIdOrderByCreatedAtDesc(owner.userId())
                : reportRepository.findByAnonymousIdOrderByCreatedAtDesc(owner.anonymousId());

        Set<Long> productIds = new HashSet<>();
        requests.values().forEach(r -> { if (r.getProductId() != null) productIds.add(r.getProductId()); });
        reports.forEach(r -> productIds.add(r.getProductId()));
        Map<Long, String> names = productIds.isEmpty() ? Map.of()
                : productRepository.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Product::getName));

        List<ContributionsResponse.RequestItem> requestItems = mine.stream()
                .filter(row -> requests.containsKey((Long) row[0]))
                .map(row -> {
                    AnalysisRequest r = requests.get((Long) row[0]);
                    return new ContributionsResponse.RequestItem(r.getId(), r.kind(), displayName(r, names), r.getProductId(),
                            r.getStatus().name(), r.getStatus().label(), (LocalDateTime) row[1]);
                })
                .toList();

        List<ContributionsResponse.ReportItem> reportItems = reports.stream()
                .map(r -> new ContributionsResponse.ReportItem(r.getId(), r.getProductId(), names.get(r.getProductId()),
                        r.getReportType().name(), r.getReportType().label(), r.getStatus().name(), r.getStatus().label(), r.getCreatedAt()))
                .toList();

        long reqDone = requestItems.stream().filter(i -> AnalysisRequestStatus.DONE.name().equals(i.status())).count();
        long reqFailed = requestItems.stream().filter(i -> AnalysisRequestStatus.FAILED.name().equals(i.status())).count();
        long repDone = reports.stream().filter(r -> r.getStatus() == ReportStatus.DONE).count();
        long repRejected = reports.stream().filter(r -> r.getStatus() == ReportStatus.REJECTED).count();
        ContributionsResponse.Summary summary = new ContributionsResponse.Summary(
                requestItems.size(), requestItems.size() - reqDone - reqFailed, reqDone, reqFailed,
                reports.size(), reports.size() - repDone - repRejected, repDone, repRejected);

        return new ContributionsResponse(summary, requestItems, reportItems);
    }

    private static String displayName(AnalysisRequest r, Map<Long, String> names) {
        if (r.getProductId() != null && names.containsKey(r.getProductId())) return names.get(r.getProductId());
        return r.identifier();
    }
}
