package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.domain.body.service.KcalBandResolver;
import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.common.util.EditDistance;
import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.enums.ListSort;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.product.service.ProductCardService;
import com.example.nutriuniv.domain.search.dto.SearchResponse;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.Candidate;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 이름 검색 (v3 SRCH_001 · SRCH_004 · SRCH_005).
 * <ul>
 *   <li>정확 · 부분 일치(이름 · 브랜드 · 공백 무시 · 초성)와 비슷한 이름(pg_trgm) · 사전 동의어를 한 목록으로 — 정렬 5가지, 20개씩</li>
 *   <li>목표는 요청으로만 받는다(저장 · 기록하지 않음). 없으면 감량. 카드는 두 목표 점수를 다 싣는다</li>
 *   <li>단종 · 생산 중단 · 점수 없음도 결과에 넣는다(배지). 점수 없음은 맨 뒤</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    public static final int MAX_QUERY_LENGTH = 30;
    public static final int MAX_PAGE_SIZE = 50;
    private static final int SAME_CATEGORY_TOP = 5;

    private final KcalBandResolver kcalBandResolver;
    private final ProductSearchRepository searchRepository;
    private final ProductListQuery listQuery;
    private final ProductCardService cardService;
    private final CategoryRepository categoryRepository;
    private final OwnerResolver ownerResolver;
    private final SearchDictionaryService dictionary;
    private final SearchSettingService settings;

    @Transactional(readOnly = true)
    public SearchResponse search(String q, String goalParam, Integer kcalBand, String sortParam, int page, int size, Actor actor) {
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
        GoalType goal = goalParam == null || goalParam.isBlank() ? GoalType.DEFAULT : GoalType.from(goalParam);
        EerBand band = kcalBandResolver.resolve(actor, kcalBand);
        int slot = EerBand.slot(goal, band);
        ListSort sort = ListSort.from(sortParam);
        Owner owner = ownerResolver.resolveOrNull(actor);

        Predicate exact = searchRepository.exactPredicate(raw, normalized, chosung);
        searchRepository.setSimilarityLimit(settings.similarityThreshold());
        ProductListQuery.Scope scope = searchRepository.matchScope(exact, normalized, dictionary.expand(normalized));

        // 이름 전체 일치 — 정렬과 상관없이 맨 위 한 칸
        Long pinnedId = chosung == null ? searchRepository.pinnedId(normalized, goal, slot).orElse(null) : null;
        ProductListQuery.Page hits = listQuery.page(scope, false, ProductListQuery.Filters.NONE, goal, slot, sort,
                pinnedId, size, page * size);

        List<Long> ids = new ArrayList<>(hits.ids());
        if (pinnedId != null && page == 0) ids.add(0, pinnedId);
        List<ProductCard> cards = cardService.cards(ids, goal, band, owner, sort);
        ProductCard pinned = null;
        if (pinnedId != null && page == 0 && !cards.isEmpty() && cards.get(0).productId().equals(pinnedId)) {
            pinned = cards.get(0);
            cards = cards.subList(1, cards.size());
        }
        long total = hits.total() + (pinnedId != null ? 1 : 0);

        // 0건 — 오타 교정 · 추정 종류의 점수 상위
        String didYouMean = null;
        List<ProductCard> sameCategoryTop = List.of();
        boolean noResult = total == 0;
        if (noResult) {
            Optional<Candidate> best = searchRepository.bestSimilar(normalized);
            didYouMean = didYouMean(normalized, best.orElse(null));
            if (best.isPresent() && best.get().categoryId() != null) {
                List<Long> top = searchRepository.topInCategory(best.get().categoryId(), goal, slot, SAME_CATEGORY_TOP);
                sameCategoryTop = cardService.cards(top, goal, band, owner, ListSort.SCORE);
            }
        }

        return new SearchResponse(raw, goal.name(), band.kcal(), sort.name(), sort.label(), total, page, size,
                (long) (page + 1) * size < hits.total(), pinned, categoryLink(normalized), cards,
                didYouMean, sameCategoryTop, noResult);
    }

    /** 검색어가 중분류(또는 합산 칩) 이름과 같으면 그 순위로 가는 길. */
    private SearchResponse.CategoryLink categoryLink(String normalized) {
        for (Category c : categoryRepository.findAll()) {
            if (!c.isActive() || c.getCode() == null || (c.getDepth() < 2 && !c.isAggregate())) continue;
            if (normalized.equals(SearchNormalizer.normalize(c.getName()))) {
                return new SearchResponse.CategoryLink(c.getCode(), c.getName());
            }
        }
        return null;
    }

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
}
