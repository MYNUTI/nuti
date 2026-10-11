package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.repository.CoupangLinkRepository;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.link.client.CoupangPageFetcher;
import com.example.nutriuniv.domain.link.client.CoupangPageFetcher.Page;
import com.example.nutriuniv.domain.link.dto.LinkFeedbackRequest;
import com.example.nutriuniv.domain.link.dto.LinkResolveRequest;
import com.example.nutriuniv.domain.link.dto.LinkResolveResponse;
import com.example.nutriuniv.domain.link.entity.LinkResolve;
import com.example.nutriuniv.domain.link.entity.LinkResolveFeedback;
import com.example.nutriuniv.domain.link.repository.LinkResolveFeedbackRepository;
import com.example.nutriuniv.domain.link.repository.LinkResolveRepository;
import com.example.nutriuniv.domain.link.util.CoupangTitleParser;
import com.example.nutriuniv.domain.link.util.LinkUrlNormalizer;
import com.example.nutriuniv.domain.link.util.LinkUrlNormalizer.Parsed;
import com.example.nutriuniv.domain.link.util.LinkUrlNormalizer.SourceType;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.Candidate;
import com.example.nutriuniv.domain.search.service.SearchSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 붙여넣은 링크 제품 인식 (API 명세 POST /links/resolve · feedback).
 * <ol>
 *   <li>URL 정규화(utm_·제휴코드 제거) → 같은 링크 24시간 캐시</li>
 *   <li>쿠팡이 아니면 외부 조회 없이 matched=null (검색 폴백)</li>
 *   <li>사용자가 예전에 바로잡은 제품이 있으면 그것(FEEDBACK)</li>
 *   <li>URL 의 쿠팡 상품 ID 가 이미 연결된 제품(coupang_links)이면 그것(COUPANG_ID)</li>
 *   <li>아니면 상품 페이지 제목을 3초 안에 읽어 검색 색인으로 매칭 — 제품명 포함(EXACT_NAME) 또는 철자 유사도(SIMILAR)</li>
 * </ol>
 * 외부 조회 실패·시간 초과·차단은 전부 matched=null 로 정상 응답한다. 클립보드 감지 ON/OFF 는 클라이언트가 /me/settings 로 판단해 호출 여부를 정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkResolveService {

    public static final Duration CACHE_TTL = Duration.ofHours(24);
    /** 제목 유사도 매칭 하한 — 검색 기준값(0.30)은 제목 매칭엔 너무 느슨하다. 둘 중 큰 값을 쓴다. */
    public static final double MIN_TITLE_SIMILARITY = 0.45;
    private static final int MIN_CONTAINED_NAME_LENGTH = 4;

    private final LinkResolveRepository resolveRepository;
    private final LinkResolveFeedbackRepository feedbackRepository;
    private final CoupangLinkRepository coupangLinkRepository;
    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final ProductSearchRepository searchRepository;
    private final SearchSettingService searchSettings;
    private final CoupangPageFetcher fetcher;
    private final OwnerResolver ownerResolver;
    private final GradeLookupService gradeLookupService;

    private record Match(Long productId, BigDecimal confidence, LinkResolve.Method method, String coupangProductId, String title) {
        static Match none(String coupangProductId, String title) {
            return new Match(null, null, LinkResolve.Method.NONE, coupangProductId, title);
        }
    }

    // ── POST /links/resolve ───────────────────────────────────────────────────────

    @Transactional
    public LinkResolveResponse resolve(LinkResolveRequest request, Actor actor) {
        Parsed parsed;
        try {
            parsed = LinkUrlNormalizer.parse(request == null ? null : request.getUrl());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
        String hash = LinkUrlNormalizer.sha256Hex(parsed.canonical());
        Owner owner = ownerResolver.resolveOrNull(actor);
        GoalType goal = gradeLookupService.resolveGoalType(owner);
        LocalDateTime now = LocalDateTime.now();

        Optional<LinkResolve> cached = resolveRepository.findFirstByUrlHashAndExpiresAtAfterOrderByCreatedAtDesc(hash, now);
        if (cached.isPresent()) {
            return toResponse(cached.get(), goal, true);
        }

        Match match = parsed.sourceType() == SourceType.UNKNOWN ? Match.none(null, null) : match(parsed, hash);
        LinkResolve saved = resolveRepository.save(LinkResolve.create(
                parsed.canonical(), hash, parsed.sourceType().name(), match.title(), match.coupangProductId(),
                match.productId(), match.confidence(), match.method(), owner, actor.sessionId(), now, now.plus(CACHE_TTL)));
        return toResponse(saved, goal, false);
    }

    private Match match(Parsed parsed, String hash) {
        // 사용자 교정 우선
        Optional<LinkResolveFeedback> corrected = feedbackRepository.findFirstByUrlHashAndCorrectedProductIdIsNotNullOrderByCreatedAtDesc(hash);
        if (corrected.isPresent() && isActiveProduct(corrected.get().getCorrectedProductId())) {
            return new Match(corrected.get().getCorrectedProductId(), BigDecimal.ONE, LinkResolve.Method.FEEDBACK, parsed.coupangProductId(), null);
        }

        String coupangId = parsed.coupangProductId();
        String title = null;
        Page page = null;

        // 단축 링크는 리다이렉트를 따라가야 상품 URL 이 나온다
        if (coupangId == null && parsed.shortLink()) {
            page = fetcher.fetch(parsed.uri()).orElse(null);
            if (page != null) {
                try {
                    coupangId = LinkUrlNormalizer.parse(page.finalUri().toString()).coupangProductId();
                } catch (IllegalArgumentException ignored) { /* 상품 URL 이 아니면 제목으로 */ }
                title = CoupangTitleParser.extractTitle(page.html()).orElse(null);
            }
        }

        // 이미 연결된 쿠팡 상품 ID
        if (coupangId != null) {
            Optional<CoupangLink> linked = coupangLinkRepository.findFirstByCoupangProductIdAndLinkStatus(coupangId, "LINKED");
            if (linked.isPresent() && linked.get().getProduct() != null && linked.get().getProduct().isActive()) {
                return new Match(linked.get().getProduct().getId(), BigDecimal.ONE, LinkResolve.Method.COUPANG_ID, coupangId, null);
            }
        }

        // 페이지 제목 → 검색 색인
        if (title == null && page == null) {
            page = fetcher.fetch(parsed.uri()).orElse(null);
            if (page != null) title = CoupangTitleParser.extractTitle(page.html()).orElse(null);
        }
        if (title == null) return Match.none(coupangId, null);

        String clean = CoupangTitleParser.clean(title);
        String normalized = SearchNormalizer.normalize(clean);
        if (normalized.isEmpty()) return Match.none(coupangId, clean);

        Optional<Candidate> best = searchRepository.bestSimilar(normalized);
        if (best.isPresent() && isActiveProduct(best.get().productId())) {
            Candidate c = best.get();
            if (c.nameNormalized() != null && c.nameNormalized().length() >= MIN_CONTAINED_NAME_LENGTH && normalized.contains(c.nameNormalized())) {
                return new Match(c.productId(), new BigDecimal("0.900"), LinkResolve.Method.EXACT_NAME, coupangId, clean);
            }
            double threshold = Math.max(searchSettings.similarityThreshold(), MIN_TITLE_SIMILARITY);
            if (c.similarity() >= threshold) {
                return new Match(c.productId(), BigDecimal.valueOf(c.similarity()).setScale(3, RoundingMode.HALF_UP),
                        LinkResolve.Method.SIMILAR, coupangId, clean);
            }
        }
        return Match.none(coupangId, clean);
    }

    private boolean isActiveProduct(Long productId) {
        return productId != null && productRepository.findById(productId).map(Product::isActive).orElse(false);
    }

    // ── POST /links/resolve/{resolveId}/feedback ──────────────────────────────────

    @Transactional
    public void feedback(String resolveId, LinkFeedbackRequest request, Actor actor) {
        LinkResolve resolve = resolveRepository.findById(resolveId == null ? "" : resolveId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 resolveId 입니다."));
        if (request == null || request.getConfirmed() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "confirmed는 필수입니다.");
        }
        Long corrected = request.getCorrectedProductId();
        if (corrected != null) {
            productRepository.findById(corrected).filter(Product::isActive)
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND, "바로잡은 제품을 찾을 수 없습니다."));
            if (corrected.equals(resolve.getMatchedProductId())) corrected = null;     // 같은 제품이면 교정이 아니다
        }
        feedbackRepository.save(LinkResolveFeedback.create(resolve, request.getConfirmed(), corrected, ownerResolver.resolveOrNull(actor)));
    }

    // ── 응답 ─────────────────────────────────────────────────────────────────────

    private LinkResolveResponse toResponse(LinkResolve r, GoalType goal, boolean cached) {
        LinkResolveResponse.Matched matched = null;
        if (r.isMatched()) {
            Product p = productRepository.findById(r.getMatchedProductId()).filter(Product::isActive).orElse(null);
            if (p != null) {
                ProductNutrient n = productNutrientRepository.findById(p.getId()).orElse(null);
                ProductStatus status = ProductStatus.of(n);
                String grade = null;
                if (status == ProductStatus.ANALYZED) {
                    grade = gradeLookupService.lookup(p.getId(), goal).map(GradeLookupService.GradeView::grade)
                            .orElseGet(() -> gradeLookupService.computeOnTheFly(n, goal).grade());
                }
                matched = LinkResolveResponse.Matched.builder()
                        .productId(p.getId())
                        .name(p.getName())
                        .imageUrl(p.getImageUrl())
                        .status(status.name())
                        .grade(grade)
                        .gradeLabel(gradeLookupService.label(goal, grade))
                        .confidence(r.getConfidence() == null ? 0.0 : r.getConfidence().doubleValue())
                        .method(r.getMatchMethod().name())
                        .build();
            }
        }
        return LinkResolveResponse.builder()
                .resolveId(r.getId())
                .sourceType(r.getSourceType())
                .parsedTitle(r.getParsedTitle())
                .cached(cached)
                .matched(matched)
                .build();
    }
}
