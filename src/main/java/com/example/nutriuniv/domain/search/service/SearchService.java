package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.domain.search.dto.PopularKeywordResponse;
import com.example.nutriuniv.domain.search.repository.PopularKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final PopularKeywordRepository popularKeywordRepository;

    // ── GET /search/keywords/popular ─────────────────────────────────────────────
    // 순위 변동(전일 대비 UP/DOWN)은 화면에 노출하지 않으므로 응답에서 제거.
    // 일별 스냅샷(popular_keyword_history)과 배치는 데이터 보존 목적으로 그대로 유지.

    @Transactional(readOnly = true)
    public List<PopularKeywordResponse> getPopularKeywords() {
        return popularKeywordRepository.findTop10ByOrderByRankAsc().stream()
                .map(pk -> PopularKeywordResponse.builder()
                        .rank(pk.getRank())
                        .keyword(pk.getKeyword())
                        .build())
                .toList();
    }
}
