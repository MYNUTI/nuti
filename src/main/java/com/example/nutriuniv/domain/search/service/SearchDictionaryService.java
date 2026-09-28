package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.common.util.EditDistance;
import com.example.nutriuniv.common.util.SearchNormalizer;
import com.example.nutriuniv.domain.search.entity.SearchSynonym;
import com.example.nutriuniv.domain.search.entity.SynonymType;
import com.example.nutriuniv.domain.search.repository.SearchSynonymRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 동의어 사전 조회 (기능명세서 6.2). 사전은 정규화 형태(소문자·공백/특수문자 제거)로만 다룬다.
 * <ul>
 *   <li>expand — 질의와 같은 묶음(대표어 + 그 대표어를 가리키는 다른 term)을 돌려준다 → 「비슷한 제품」 절에서 이름 포함 검색</li>
 *   <li>correctionCandidates — 편집거리 ≤ max 인 사전 항목(오타 교정 후보)</li>
 *   <li>keywordsStartingWith — 자동완성의 KEYWORD 제안</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchDictionaryService {

    private static final int MAX_EXPANSION = 6;

    private final SearchSynonymRepository repository;

    @Transactional(readOnly = true)
    public List<String> expand(String normalized) {
        if (normalized == null || normalized.isEmpty()) return List.of();
        Set<String> out = new LinkedHashSet<>();
        for (SearchSynonym s : repository.findByTermAndIsActiveTrue(normalized)) {
            out.add(s.getCanonical());
            repository.findByCanonicalAndIsActiveTrue(s.getCanonical()).forEach(x -> out.add(x.getTerm()));
        }
        repository.findByCanonicalAndIsActiveTrue(normalized).forEach(x -> out.add(x.getTerm()));   // 질의가 대표어인 경우
        out.remove(normalized);
        return out.stream().limit(MAX_EXPANSION).toList();
    }

    /** 편집거리 ≤ maxDistance 인 사전 단어(term·canonical), 가까운 순 → 같은 거리면 짧은 것 먼저. 질의 자신은 제외. */
    @Transactional(readOnly = true)
    public List<String> correctionCandidates(String normalized, int maxDistance) {
        if (normalized == null || normalized.isEmpty()) return List.of();
        Set<String> words = new LinkedHashSet<>();
        for (SearchSynonym s : repository.findByIsActiveTrue()) {
            words.add(s.getTerm());
            words.add(s.getCanonical());
        }
        words.remove(normalized);
        List<String> out = new ArrayList<>();
        for (String w : words) {
            if (EditDistance.within(normalized, w, maxDistance)) out.add(w);
        }
        out.sort(Comparator.comparingInt((String w) -> EditDistance.levenshtein(normalized, w)).thenComparingInt(String::length));
        return out;
    }

    /** 자동완성용 — 접두가 같은 사전 항목의 대표어. */
    @Transactional(readOnly = true)
    public List<String> keywordsStartingWith(String normalized, int limit) {
        if (normalized == null || normalized.isEmpty() || limit <= 0) return List.of();
        Set<String> out = new LinkedHashSet<>();
        for (SearchSynonym s : repository.findByIsActiveTrue()) {
            if (s.getTerm().startsWith(normalized) || s.getCanonical().startsWith(normalized)) {
                out.add(s.getCanonical());
                if (out.size() >= limit) break;
            }
        }
        return new ArrayList<>(out);
    }

    // ── 시드 ─────────────────────────────────────────────────────────────────────

    /**
     * 사전이 비어 있을 때만 예시 항목을 넣는다. 본 사전(400~600항목)은 팀이 채운다 — db/manual/06_search.sql 적재 템플릿 참조.
     * 형식: {term, canonical, type}. 저장 전 정규화한다.
     */
    private static final String[][] STARTER = {
            {"프로틴", "단백질", "SYNONYM"}, {"protein", "단백질", "SYNONYM"},
            {"단백질바", "프로틴바", "SYNONYM"}, {"protein bar", "프로틴바", "SYNONYM"},
            {"zero", "제로", "SYNONYM"}, {"슈가프리", "무설탕", "SYNONYM"}, {"sugar free", "무설탕", "SYNONYM"},
            {"로우슈가", "저당", "SYNONYM"}, {"요구르트", "요거트", "SYNONYM"}, {"yogurt", "요거트", "SYNONYM"},
            {"soy milk", "두유", "SYNONYM"}, {"닭가슴", "닭가슴살", "SYNONYM"}, {"oatmeal", "오트밀", "SYNONYM"},
            {"granola", "그래놀라", "SYNONYM"},
            {"maeil", "매일", "BRAND_ALIAS"}, {"almond breeze", "아몬드브리즈", "BRAND_ALIAS"},
            {"pulmuone", "풀무원", "BRAND_ALIAS"}, {"ottogi", "오뚜기", "BRAND_ALIAS"},
    };

    @Transactional
    public void ensureStarterSeed() {
        if (repository.count() > 0) return;
        List<SearchSynonym> rows = new ArrayList<>();
        for (String[] r : STARTER) {
            rows.add(SearchSynonym.create(SearchNormalizer.normalize(r[0]), SearchNormalizer.normalize(r[1]), SynonymType.valueOf(r[2])));
        }
        repository.saveAll(rows);
        log.info("[SEARCH] 동의어 사전 예시 {}건 시드 — 본 사전은 팀이 채운다", rows.size());
    }
}
