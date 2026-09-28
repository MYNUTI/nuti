package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.search.dto.SearchSuggestionResponse;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository;
import com.example.nutriuniv.domain.search.repository.ProductSearchRepository.SuggestRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 자동완성 (기능명세서 6.2). 초성·공백 무시 색인으로 접두→포함 순으로 찾고, 모자라면 사전 대표어(KEYWORD), 그래도 모자라면 철자 유사도(오타).
 * 1자 이하 → 빈 목록(200). 30자 초과 400. 응답 지연 시 빈 목록으로 넘기는 것은 클라이언트 몫(200ms) — 여기서는 쿼리를 작게 유지한다.
 */
@Service
@RequiredArgsConstructor
public class SearchSuggestionService {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;

    private final ProductSearchRepository searchRepository;
    private final SearchDictionaryService dictionary;
    private final SearchSettingService settings;

    @Transactional(readOnly = true)
    public SearchSuggestionResponse suggest(String q, Integer sizeParam) {
        String raw = q == null ? "" : q.trim();
        if (raw.length() > ProductSearchService.MAX_QUERY_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "검색어는 " + ProductSearchService.MAX_QUERY_LENGTH + "자 이하여야 합니다.");
        }
        int size = sizeParam == null || sizeParam <= 0 ? DEFAULT_SIZE : Math.min(sizeParam, MAX_SIZE);
        String normalized = SearchNormalizer.normalize(raw);
        if (normalized.length() <= 1) {
            return SearchSuggestionResponse.builder().items(List.of()).build();       // 1자 이하 → 빈 목록
        }
        String chosung = SearchNormalizer.isChosungQuery(raw) ? SearchNormalizer.chosung(raw) : null;

        List<SearchSuggestionResponse.Item> items = new ArrayList<>(size);
        Set<Long> seenProducts = new LinkedHashSet<>();

        // 1) 접두 → 포함 (공백 무시 · 초성)
        for (SuggestRow row : searchRepository.suggest(normalized, chosung, size)) {
            if (seenProducts.add(row.productId())) {
                items.add(productItem(row, raw, normalized, chosung));
            }
        }
        // 2) 사전 대표어
        if (items.size() < size) {
            for (String keyword : dictionary.keywordsStartingWith(normalized, size - items.size())) {
                int[] r = SearchNormalizer.matchRange(null, normalized, null, keyword);
                items.add(SearchSuggestionResponse.Item.builder()
                        .type("KEYWORD").name(keyword).matchedRanges(ranges(r)).build());
            }
        }
        // 3) 오타 보정 — 철자 유사도
        if (items.size() < size) {
            for (SuggestRow row : searchRepository.suggestSimilar(normalized, settings.similarityThreshold(), size - items.size() + seenProducts.size())) {
                if (items.size() >= size) break;
                if (seenProducts.add(row.productId())) {
                    items.add(productItem(row, raw, normalized, chosung));
                }
            }
        }
        return SearchSuggestionResponse.builder().items(items).build();
    }

    private static SearchSuggestionResponse.Item productItem(SuggestRow row, String raw, String normalized, String chosung) {
        int[] r = SearchNormalizer.matchRange(raw, normalized, chosung, row.name());
        return SearchSuggestionResponse.Item.builder()
                .type("PRODUCT")
                .productId(row.productId())
                .name(row.name())
                .brandName(row.brandName())
                .matchedRanges(ranges(r))
                .build();
    }

    private static List<List<Integer>> ranges(int[] r) {
        return r == null ? List.of() : List.of(List.of(r[0], r[1]));
    }
}
