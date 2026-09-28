package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.common.util.EditDistance;
import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.grade.entity.ProductGrade;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.search.dto.SearchResponse;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.Candidate;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.Hit;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.Predicate;
import com.example.nutriuniv.domain.search.util.SearchHighlight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 검색 (기능명세서 6.1) — 4-way(그대로 포함·철자 유사도·공백/특수문자 제거·초성) + 동의어, 정확 일치/비슷한 제품 분리, 등급순 정렬,
 * 영양정보 부족 제품 노출(맨 뒤), 목표 기준 위반 문구, 0건 폴백 4단계.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    public static final int MAX_QUERY_LENGTH = 30;
    public static final int MAX_PAGE_SIZE = 50;
    private static final int SAME_CATEGORY_TOP = 5;

    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final ProductGradeRepository productGradeRepository;
    private final SavedProductRepository savedProductRepository;
    private final OwnerResolver ownerResolver;
    private final GradeLookupService gradeLookupService;
    private final SearchDictionaryService dictionary;
    private final SearchSettingService settings;

    @Transactional(readOnly = true)
    public SearchResponse search(String q, String goalParam, int page, int size, Actor actor) {
        String raw = q == null ? "" : q.trim();
        if (raw.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "검색어(q)는 필수입니다.");
        }
        if (raw.length() > MAX_QUERY_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "검색어는 " + MAX_QUERY_LENGTH + "자 이하여야 합니다.");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        String normalized = SearchNormalizer.normalize(raw);
        if (normalized.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "검색어에 글자나 숫자가 없습니다.");
        }
        String chosung = SearchNormalizer.isChosungQuery(raw) ? SearchNormalizer.chosung(raw) : null;

        Owner owner = ownerResolver.resolveOrNull(actor);
        GoalType goal = goalParam != null && !goalParam.isBlank()
                ? GoalType.from(goalParam)
                : gradeLookupService.resolveGoalType(owner);     // 없으면 내 목표, 그것도 없으면 GENERAL
        int slot = EerBand.defaultSlot(goal);
        double threshold = settings.similarityThreshold();

        // 정확 일치
        Predicate exact = searchRepository.exactPredicate(raw, normalized, chosung);
        long exactTotal = searchRepository.count(exact);
        List<Hit> exactHits = searchRepository.find(exact, goal, slot, null, size, page * size);

        // 비슷한 제품 (정확 일치 제외)
        List<String> synonyms = dictionary.expand(normalized);
        Predicate similar = searchRepository.similarPredicate(exact, normalized, threshold, synonyms);
        long similarTotal = searchRepository.count(similar);
        List<Hit> similarHits = searchRepository.find(similar, goal, slot, normalized, size, page * size);

        // 0건 폴백
        String didYouMean = null;
        List<Long> sameCategoryIds = List.of();
        boolean noResult = exactTotal == 0 && similarTotal == 0;
        if (noResult) {
            Optional<Candidate> best = searchRepository.bestSimilar(normalized);
            didYouMean = didYouMean(normalized, best.orElse(null));
            if (best.isPresent() && best.get().categoryId() != null) {
                sameCategoryIds = searchRepository.topInCategory(best.get().categoryId(), goal, slot, SAME_CATEGORY_TOP);
            }
        }

        // 아이템 조립 (한 번에 로드 — N+1 방지)
        List<Long> allIds = new ArrayList<>();
        exactHits.forEach(h -> allIds.add(h.productId()));
        similarHits.forEach(h -> allIds.add(h.productId()));
        allIds.addAll(sameCategoryIds);
        ItemContext ctx = loadContext(allIds, goal, slot, owner);

        return SearchResponse.builder()
                .query(raw)
                .appliedGoal(goal.name())
                .exactMatches(SearchResponse.Section.builder().total(exactTotal).items(toItems(exactHits.stream().map(Hit::productId).toList(), ctx)).build())
                .similarProducts(SearchResponse.Section.builder().total(similarTotal).items(toItems(similarHits.stream().map(Hit::productId).toList(), ctx)).build())
                .didYouMean(didYouMean)
                .sameCategoryTop(toItems(sameCategoryIds, ctx))
                .analysisRequest(SearchResponse.AnalysisRequest.builder().available(noResult).build())
                .build();
    }

    // ── 폴백 ─────────────────────────────────────────────────────────────────────

    /** 오타 교정 — 편집거리 ≤ 상한이고, 교정한 결과로 실제 검색되는 것이 있을 때만. 사전 → 가장 비슷한 제품명 순. */
    private String didYouMean(String normalized, Candidate best) {
        int maxDistance = settings.didYouMeanMaxDistance();
        for (String word : dictionary.correctionCandidates(normalized, maxDistance)) {
            if (searchRepository.count(searchRepository.exactPredicate(word, word, null)) > 0) {
                return word;
            }
        }
        if (best != null && best.nameNormalized() != null
                && EditDistance.within(normalized, best.nameNormalized(), maxDistance)) {
            return best.name();
        }
        return null;
    }

    // ── 아이템 ───────────────────────────────────────────────────────────────────

    private record ItemContext(Map<Long, Product> products, Map<Long, ProductGrade> grades,
                               Map<Long, ProductNutrient> nutrients, Set<Long> savedIds,
                               GoalType goal, Map<Nutrient, Double> thresholds) {}

    private ItemContext loadContext(List<Long> ids, GoalType goal, int slot, Owner owner) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return new ItemContext(Map.of(), Map.of(), Map.of(), Set.of(), goal, Map.of());
        }
        Map<Long, Product> products = productRepository.findByIdInAndIsActiveTrue(distinct).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductGrade> grades = productGradeRepository.findByProductIdInAndGoalAndEerBand(distinct, goal, slot).stream()
                .collect(Collectors.toMap(ProductGrade::getProductId, Function.identity()));
        Map<Long, ProductNutrient> nutrients = productNutrientRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(ProductNutrient::getId, Function.identity()));
        Set<Long> savedIds = owner == null ? Set.of()
                : owner.isUser() ? savedProductRepository.findSavedProductIdsByUserId(owner.userId())
                : savedProductRepository.findSavedProductIdsByAnonymousId(owner.anonymousId());
        return new ItemContext(products, grades, nutrients, savedIds, goal, Map.of());
    }

    private List<SearchResponse.Item> toItems(List<Long> ids, ItemContext ctx) {
        List<SearchResponse.Item> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Product p = ctx.products().get(id);
            if (p == null) continue;                                    // 비활성 전환 등
            ProductNutrient n = ctx.nutrients().get(id);
            ProductStatus status = ProductStatus.of(n);

            String grade = null, label = null, highlight = null;
            if (status == ProductStatus.ANALYZED) {
                ProductGrade row = ctx.grades().get(id);
                String topPenalty;
                if (row != null) {
                    grade = row.getGrade().name();
                    topPenalty = row.getTopPenaltyNutrient() == null ? null : row.getTopPenaltyNutrient().name();
                } else {                                                // 배치 전 — 즉석 계산
                    GradeLookupService.GradeView view = gradeLookupService.computeOnTheFly(n, ctx.goal());
                    grade = view.grade();
                    topPenalty = view.topPenaltyNutrient();
                }
                label = gradeLookupService.label(ctx.goal(), grade);
                if (topPenalty != null) {
                    Nutrient nutrient = Nutrient.valueOf(topPenalty);
                    highlight = SearchHighlight.build(ctx.goal(), nutrient, per100g(n, nutrient));
                }
            }

            items.add(SearchResponse.Item.builder()
                    .productId(p.getId())
                    .name(p.getName())
                    .brandName(p.getBrand() == null ? null : p.getBrand().getName())
                    .imageUrl(p.getImageUrl())
                    .status(status.name())
                    .grade(grade)
                    .gradeLabel(label)
                    .highlight(highlight)
                    .saved(ctx.savedIds().contains(p.getId()))
                    .build());
        }
        return items;
    }

    private static BigDecimal per100g(ProductNutrient n, Nutrient nutrient) {
        return switch (nutrient) {
            case CALORIES      -> n.getCaloriesPer100g();
            case PROTEIN       -> n.getProteinPer100g();
            case DIETARY_FIBER -> n.getFiberPer100g();
            case SUGAR         -> n.getSugarPer100g();
            case SATURATED_FAT -> n.getSaturatedFatPer100g();
            case TRANS_FAT     -> n.getTransFatPer100g();
            case CHOLESTEROL   -> n.getCholesterolPer100g();
            case SODIUM        -> n.getSodiumPer100g();
        };
    }
}
