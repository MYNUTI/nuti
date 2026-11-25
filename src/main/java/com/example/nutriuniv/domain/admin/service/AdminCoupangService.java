package com.example.nutriuniv.domain.admin.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangBulkSyncResponse;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangLinkPageResponse;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangLinkResponse;
import com.example.nutriuniv.domain.admin.dto.AdminCoupangSyncResponse;
import com.example.nutriuniv.domain.coupang.client.CoupangApiClient;
import com.example.nutriuniv.domain.coupang.dto.CoupangProductData;
import com.example.nutriuniv.domain.coupang.dto.CoupangSearchResponse;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.repository.CoupangLinkRepository;
import com.example.nutriuniv.domain.coupang.service.CoupangCandidatePicker;
import com.example.nutriuniv.domain.coupang.service.CoupangCandidatePicker.Decision;
import com.example.nutriuniv.domain.coupang.service.CoupangCandidatePicker.Pick;
import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 쿠팡 연결 운영 (DATA_006 · 11-24).
 * <ul>
 *   <li>연결: 제품 이름으로 쿠팡을 검색해 이름 유사도로 고르고, 상품명의 1개 용량 · 개수로 같은 용량 → 다른 용량 순으로 잇는다.
 *       한쪽이라도 용량이나 개수가 없으면 자동으로 잇지 않고 확인 대기(REVIEW)로 둔다 — {@link CoupangCandidatePicker}</li>
 *   <li>확인: 운영자가 REVIEW 후보를 같은 용량 / 다른 용량 / 아님으로 정한다</li>
 *   <li>가격 새로 받기: 연결한 상품을 다시 검색해 판매가 · 조회한 날을 새로 받는다. 쿠팡 응답에 품절 칸이 없어서,
 *       검색은 됐는데 연결한 상품이 두 번 이어서 안 나오면 「판매 상태 확인 못 함」(가격 숨김 · 버튼은 쿠팡 검색 결과).
 *       API 오류는 세지 않고 직전 값을 그대로 둔다</li>
 * </ul>
 * 일괄 작업은 쿠팡 호출 앞마다 3초 쉰다(분당 50회 한도 대비 · 1분 20회).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCoupangService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of(CoupangLink.LINKED, CoupangLink.UNLINKED, CoupangLink.FAILED, CoupangLink.REVIEW);
    private static final long THROTTLE_MS = 3000;

    private final CoupangLinkRepository coupangLinkRepository;
    private final ProductRepository productRepository;
    private final CoupangApiClient coupangApiClient;

    /** 운영자 확인 결과. */
    public enum ReviewDecision { SAME, OTHER, REJECT }

    @FunctionalInterface
    interface Searcher {
        CoupangSearchResponse.SearchData search(String keyword) throws InterruptedException;
    }

    // ── GET /admin/coupang/links ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminCoupangLinkPageResponse getCoupangLinks(String status, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1 이상이어야 합니다.");
        }
        if (status != null && !ALLOWED_STATUSES.contains(status.toUpperCase())) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "허용되지 않는 status 값입니다. (LINKED / UNLINKED / FAILED / REVIEW)");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<CoupangLink> linkPage = (status == null)
                ? coupangLinkRepository.findAll(pageable)
                : coupangLinkRepository.findByLinkStatus(status.toUpperCase(), pageable);

        return AdminCoupangLinkPageResponse.builder()
                .total(linkPage.getTotalElements())
                .items(linkPage.getContent().stream().map(AdminCoupangLinkResponse::from).toList())
                .build();
    }

    // ── POST /admin/coupang/sync/{productId} ──────────────────────────────────────

    @Transactional
    public AdminCoupangSyncResponse syncCoupangLink(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 상품입니다."));
        CoupangLink link = coupangLinkRepository.findByProduct(product)
                .orElseGet(() -> coupangLinkRepository.save(CoupangLink.createDefault(product)));
        try {
            match(link, product, coupangApiClient::searchProduct, "CoupangSync");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[CoupangSync] 쿠팡 API 연동 실패 - productId: {}, error: {}", productId, e.getMessage());
            link.syncFailed();
        }
        return AdminCoupangSyncResponse.from(link);
    }

    // ── POST /admin/coupang/sync · /admin/coupang/retry ───────────────────────────

    public AdminCoupangBulkSyncResponse bulkSyncCoupangLinks() {
        List<CoupangLink> targets = coupangLinkRepository.findAllByLinkStatus(CoupangLink.UNLINKED);
        log.info("[CoupangBulkSync] UNLINKED 연결 대상 수: {}", targets.size());
        return processBulk(targets, "CoupangBulkSync");
    }

    public AdminCoupangBulkSyncResponse retryCoupangLinks() {
        List<CoupangLink> targets = coupangLinkRepository.findAllByLinkStatus(CoupangLink.FAILED);
        log.info("[CoupangRetry] FAILED 재시도 시작 — 대상: {}건, 호출 간격: 3초, 점수 문턱: {}",
                targets.size(), CoupangCandidatePicker.SCORE_THRESHOLD);
        return processBulk(targets, "CoupangRetry");
    }

    private AdminCoupangBulkSyncResponse processBulk(List<CoupangLink> targets, String logTag) {
        int linked = 0, review = 0, failed = 0, idx = 0;
        for (CoupangLink link : targets) {
            idx++;
            Product product = link.getProduct();
            try {
                Decision d = match(link, product, this::throttledSearch, logTag);
                if (d == Decision.SAME || d == Decision.OTHER) linked++;
                else if (d == Decision.REVIEW) review++;
                else failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] 사용자 중단 — 진행 {}/{}, 연결 {}, 확인 대기 {}, 실패 {}", logTag, idx - 1, targets.size(), linked, review, failed);
                break;
            } catch (Exception e) {
                log.error("[{}] 예외 — productId={}, error={}", logTag, product.getId(), e.getMessage());
                link.syncFailed();
                failed++;
            }
            coupangLinkRepository.save(link);
        }
        log.info("[{}] 완료 — 전체: {}, 연결: {}, 확인 대기: {}, 실패: {}", logTag, targets.size(), linked, review, failed);
        return AdminCoupangBulkSyncResponse.builder()
                .totalCount(targets.size())
                .successCount(linked)
                .reviewCount(review)
                .failCount(failed)
                .build();
    }

    /**
     * 키워드를 최대 3단계로 줄여 가며 찾는다(원본 → 마지막 낱말 뺌 → 브랜드). 이름 점수가 문턱을 넘는 후보가 나온 단계에서 고른다.
     * 고른 결과대로 링크를 바꾸고 판정을 돌려준다.
     */
    private Decision match(CoupangLink link, Product product, Searcher searcher, String logTag) throws InterruptedException {
        String name = product.getName();
        for (String kw : CoupangCandidatePicker.keywordVariants(name)) {
            CoupangSearchResponse.SearchData data = searcher.search(kw);
            if (data == null || data.getProductData() == null || data.getProductData().isEmpty()) continue;
            Pick pick = CoupangCandidatePicker.pick(data.getProductData(), name);
            if (pick.decision() == Decision.NONE) {
                log.info("[{}] productId={} keyword='{}' — 문턱 넘는 후보 없음(최고 {}점)",
                        logTag, product.getId(), kw, CoupangCandidatePicker.bestScore(data.getProductData(), name));
                continue;
            }
            CoupangProductData c = pick.data();
            if (pick.decision() == Decision.REVIEW) {
                link.syncReview(String.valueOf(c.getProductId()), c.getProductName(), c.getProductUrl(), data.getLandingUrl(),
                        c.getProductImage(), c.getProductPrice(), c.getIsRocket(), c.getIsFreeShipping());
            } else {
                link.syncSuccess(String.valueOf(c.getProductId()), c.getProductName(), c.getProductUrl(), data.getLandingUrl(),
                        c.getProductImage(), c.getProductPrice(), c.getIsRocket(), c.getIsFreeShipping());
                product.updateImageUrl(c.getProductImage());
            }
            link.useKeyword(kw);
            log.info("[{}] productId={} — {} (score={}, rule={}, keyword='{}', 쿠팡='{}')",
                    logTag, product.getId(), pick.decision(), pick.score(), pick.rule(), kw, c.getProductName());
            return pick.decision();
        }
        link.syncFailed();
        log.warn("[{}] productId={} keyword='{}' — 모든 단계에서 못 찾음", logTag, product.getId(), name);
        return Decision.NONE;
    }

    // ── POST /admin/coupang/links/{productId}/review ──────────────────────────────

    /** 확인 대기 후보를 운영자가 정한다 — 같은 용량 · 다른 용량이면 연결, 아니면 찾지 못함(FAILED). */
    @Transactional
    public AdminCoupangSyncResponse review(Long productId, ReviewDecision decision) {
        if (decision == null) throw new CustomException(ErrorCode.VALIDATION_FAILED, "decision 은 SAME / OTHER / REJECT 중 하나입니다.");
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 상품입니다."));
        CoupangLink link = coupangLinkRepository.findByProduct(product)
                .filter(l -> CoupangLink.REVIEW.equals(l.getLinkStatus()))
                .orElseThrow(() -> new CustomException(ErrorCode.STATE_CONFLICT, "확인 대기(REVIEW) 중인 쿠팡 연결이 없습니다."));
        switch (decision) {
            case SAME -> link.resolveReview(PurchaseMatchType.EXACT.name());
            case OTHER -> link.resolveReview(PurchaseMatchType.OTHER_SIZE.name());
            case REJECT -> link.resolveReview(null);
        }
        if (decision != ReviewDecision.REJECT) product.updateImageUrl(link.getCoupangImageUrl());
        return AdminCoupangSyncResponse.from(link);
    }

    // ── POST /admin/coupang/refresh · /admin/coupang/refresh/{productId} ──────────

    /** 연결된 상품 하나의 판매가를 새로 받는다. */
    @Transactional
    public AdminCoupangSyncResponse refresh(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 상품입니다."));
        CoupangLink link = coupangLinkRepository.findByProduct(product)
                .filter(l -> CoupangLink.LINKED.equals(l.getLinkStatus()))
                .orElseThrow(() -> new CustomException(ErrorCode.STATE_CONFLICT, "연결(LINKED)된 쿠팡 상품이 없습니다."));
        try {
            refreshOne(link, coupangApiClient.searchOrEmpty(keyword(link)));
        } catch (Exception e) {
            log.warn("[CoupangRefresh] productId={} — 쿠팡 API 실패, 직전 값 유지: {}", productId, e.getMessage());
        }
        return AdminCoupangSyncResponse.from(link);
    }

    /** 연결된 상품 전부의 판매가를 새로 받는다(호출마다 3초). 성공 = 다시 검색됨, 실패 = 검색 안 됨 · API 오류. */
    public AdminCoupangBulkSyncResponse refreshAll() {
        List<CoupangLink> targets = coupangLinkRepository.findAllByLinkStatus(CoupangLink.LINKED);
        int found = 0, missed = 0, errors = 0, unknown = 0;
        for (CoupangLink link : targets) {
            try {
                Thread.sleep(THROTTLE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                if (refreshOne(link, coupangApiClient.searchOrEmpty(keyword(link)))) found++;
                else missed++;
                if (link.saleUnknown()) unknown++;
                coupangLinkRepository.save(link);
            } catch (Exception e) {
                errors++;
                log.warn("[CoupangRefresh] linkId={} — 쿠팡 API 실패, 직전 값 유지: {}", link.getId(), e.getMessage());
            }
        }
        log.info("[CoupangRefresh] 완료 — 전체 {}, 다시 검색됨 {}, 안 나옴 {}(판매 상태 확인 못 함 {}), API 실패 {}",
                targets.size(), found, missed, unknown, errors);
        return AdminCoupangBulkSyncResponse.builder()
                .totalCount(targets.size())
                .successCount(found)
                .failCount(missed + errors)
                .build();
    }

    /** 검색 결과에서 연결한 쿠팡 상품(같은 상품 번호)을 찾아 값을 새로 받는다. 없으면 연속으로 안 나온 횟수를 올린다. */
    public static boolean refreshOne(CoupangLink link, CoupangSearchResponse.SearchData data) {
        List<CoupangProductData> items = data.getProductData() == null ? List.of() : data.getProductData();
        CoupangProductData same = items.stream()
                .filter(c -> c != null && c.getProductId() != null
                        && Objects.equals(String.valueOf(c.getProductId()), link.getCoupangProductId()))
                .findFirst().orElse(null);
        if (same == null) {
            link.refreshMissed();
            return false;
        }
        link.refreshFound(same.getProductName(), same.getProductUrl(), same.getProductImage(),
                same.getProductPrice(), same.getIsRocket(), same.getIsFreeShipping());
        return true;
    }

    /** 그 상품을 찾은 검색어(처음엔 제품 이름). */
    private static String keyword(CoupangLink link) {
        String kw = link.getSearchKeyword();
        return kw == null || kw.isBlank() ? link.getProduct().getName() : kw;
    }

    private CoupangSearchResponse.SearchData throttledSearch(String keyword) throws InterruptedException {
        Thread.sleep(THROTTLE_MS);
        return coupangApiClient.searchProduct(keyword);
    }
}
