package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.domain.search.entity.SearchSetting;
import com.example.nutriuniv.domain.search.repository.SearchSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검색 기준값 — DB(search_settings) 우선, 없거나 파싱 실패면 코드 기본값. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSettingService {

    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.30;
    public static final int DEFAULT_DID_YOU_MEAN_MAX_DISTANCE = 2;

    private final SearchSettingRepository repository;

    @Transactional(readOnly = true)
    public double similarityThreshold() {
        return repository.findById(SearchSetting.SIMILARITY_THRESHOLD)
                .map(s -> parseDouble(s.getValue(), DEFAULT_SIMILARITY_THRESHOLD))
                .orElse(DEFAULT_SIMILARITY_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public int didYouMeanMaxDistance() {
        return repository.findById(SearchSetting.DID_YOU_MEAN_MAX_DISTANCE)
                .map(s -> parseInt(s.getValue(), DEFAULT_DID_YOU_MEAN_MAX_DISTANCE))
                .orElse(DEFAULT_DID_YOU_MEAN_MAX_DISTANCE);
    }

    /** 부팅 시 기본값 행이 없으면 넣는다 (있으면 손대지 않음 — 관리자가 바꾼 값 보존). */
    @Transactional
    public void ensureDefaults() {
        if (!repository.existsById(SearchSetting.SIMILARITY_THRESHOLD)) {
            repository.save(SearchSetting.create(SearchSetting.SIMILARITY_THRESHOLD, String.valueOf(DEFAULT_SIMILARITY_THRESHOLD),
                    "pg_trgm 철자 유사도 하한 (0~1). 비슷한 제품·오타 보정 후보에 적용. 초기값은 추정 — 실측으로 조정"));
        }
        if (!repository.existsById(SearchSetting.DID_YOU_MEAN_MAX_DISTANCE)) {
            repository.save(SearchSetting.create(SearchSetting.DID_YOU_MEAN_MAX_DISTANCE, String.valueOf(DEFAULT_DID_YOU_MEAN_MAX_DISTANCE),
                    "didYouMean 제안의 편집거리 상한 (기능명세서: ≤2)"));
        }
    }

    private static double parseDouble(String v, double def) {
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            log.warn("[SEARCH] 설정값 파싱 실패 '{}' — 기본값 {}", v, def);
            return def;
        }
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            log.warn("[SEARCH] 설정값 파싱 실패 '{}' — 기본값 {}", v, def);
            return def;
        }
    }
}
