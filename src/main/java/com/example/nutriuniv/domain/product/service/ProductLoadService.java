package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.product.entity.ProductLoad;
import com.example.nutriuniv.domain.product.repository.ProductLoadRepository;
import com.example.nutriuniv.domain.ranking.service.ChipStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 적재 기록 (v3 DATA_000) — 적재 중에는 다른 적재와 등급 기준 재산출(CORE_007)을 막는다.
 * 서버가 적재 도중 꺼지면 RUNNING 이 남는다 — 서버가 켜질 때 {@link #failInterrupted()} 가 실패로 돌린다(서버는 한 대라
 * 켜지는 순간 진행 중인 적재는 없다). 그 전에도 막히지 않도록 시작한 지 STALE_AFTER 가 지난 RUNNING 은 진행 중으로 보지 않는다.
 * 기록은 적재 행들과 따로 커밋한다(REQUIRES_NEW) — 중간에 실패해도 기록은 남는다.
 */
@Service
@RequiredArgsConstructor
public class ProductLoadService {

    static final Duration STALE_AFTER = Duration.ofHours(2);

    private final ProductLoadRepository loadRepository;
    private final ChipStatsService chipStatsService;

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
        chipStatsService.invalidate();   // 칩 개수 · 게이트 · 업데이트 시각을 다음 조회에서 다시 센다
    }

    /** 부팅 때 — 꺼지기 전에 돌던 적재 기록(RUNNING)을 실패로. 돌린 건수. */
    @Transactional
    public int failInterrupted() {
        return loadRepository.failInterrupted(LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long loadId) {
        loadRepository.findById(loadId).ifPresent(ProductLoad::fail);
    }
}
