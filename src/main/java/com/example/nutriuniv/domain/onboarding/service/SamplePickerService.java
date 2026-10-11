package com.example.nutriuniv.domain.onboarding.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.onboarding.dto.SamplePickerResponse;
import com.example.nutriuniv.domain.onboarding.repository.SamplePickerRepository;
import com.example.nutriuniv.domain.onboarding.repository.SamplePickerRepository.Sample;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 샘플 픽커 (기능명세서 2.2) — 바코드가 없는 사용자의 주요 경로. 실패해도 온보딩을 멈추지 않는 것은 클라이언트 몫(검색 시작 화면 대체).
 */
@Service
@RequiredArgsConstructor
public class SamplePickerService {

    public static final int DEFAULT_SIZE = 6;
    public static final int MAX_SIZE = 12;

    private final SamplePickerRepository repository;
    private final CurationWarningStore warningStore;

    @Transactional(readOnly = true)
    public SamplePickerResponse pick(Integer sizeParam) {
        int size = sizeParam == null ? DEFAULT_SIZE : sizeParam;
        if (size < 1) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "size는 1 이상이어야 합니다.");
        }
        size = Math.min(size, MAX_SIZE);

        List<Sample> curated  = repository.curated(50);
        List<Sample> popularA = repository.popularByGrade(Grade.A.name(), 5);
        List<Sample> popularD = repository.popularByGrade(Grade.D.name(), 5);
        List<Sample> popular  = repository.popular(size * 2);

        SampleComposer.Result result = SampleComposer.compose(curated, popularA, popularD, popular, size);
        result.warnings().forEach(warningStore::add);

        return SamplePickerResponse.builder()
                .items(result.items().stream()
                        .map(s -> SamplePickerResponse.Item.builder()
                                .productId(s.productId()).name(s.name()).imageUrl(s.imageUrl()).grade(s.grade()).build())
                        .toList())
                .build();
    }
}
