package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.product.entity.ProductLoad;
import com.example.nutriuniv.domain.product.repository.ProductLoadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 적재 기록 (v3 DATA_000) — 적재 중에는 다른 적재와 등급 기준 재산출(CORE_007)을 막는다.
 * 서버가 적재 도중 꺼지면 RUNNING 이 남으므로, 시작한 지 STALE_AFTER 가 지난 RUNNING 은 진행 중으로 보지 않는다.
 * 기록은 적재 행들과 따로 커밋한다(REQUIRES_NEW) — 중간에 실패해도 기록은 남는다.
 */
@Service
@RequiredArgsConstructor
public class ProductLoadService {

    static final Duration STALE_AFTER = Duration.ofHours(2);

    private final ProductLoadRepository loadRepository;

    @Transactional(readOnly = true)
    public boolean isLoading() {
        return loadRepository.existsByStatusAndStartedAtAfter(ProductLoad.Status.RUNNING, LocalDateTime.now().minus(STALE_AFTER));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String fileName) {
        if (isLoading()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "다른 제품 적재가 진행 중입니다. 끝난 뒤 다시 올려 주세요.");
        }
        return loadRepository.save(ProductLoad.start(fileName)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long loadId, int total, int created, int updated, int unchanged, int protectedRows, int failed) {
        loadRepository.findById(loadId).ifPresent(l -> l.finish(total, created, updated, unchanged, protectedRows, failed));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long loadId) {
        loadRepository.findById(loadId).ifPresent(ProductLoad::fail);
    }
}
