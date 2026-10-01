package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.domain.ranking.repository.RankingCategoryRepository;
import com.example.nutriuniv.domain.ranking.repository.RankingCategoryStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 랭킹 준비 — 선정 기준 문구 시드, 그리고 서비스 분류는 있는데 배치 결과가 없으면(매핑을 새로 넣은 직후) 백그라운드 재계산.
 * 서비스 분류가 없으면(분류 체계 결정 대기) 아무 일도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingInitializer implements ApplicationRunner {

    private final RankingCopySeeder copySeeder;
    private final RankingCategoryRepository categoryRepository;
    private final RankingCategoryStatRepository statRepository;
    private final RankingBatchService batchService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            copySeeder.ensureSeeded();
        } catch (Exception e) {
            log.error("[RANKING] 선정 기준 문구 시드 실패", e);
        }
        try {
            long active = categoryRepository.countByIsActiveTrue();
            if (active > 0 && statRepository.count() < active) {
                log.info("[RANKING] 서비스 분류 {}개 중 배치 결과가 없는 분류가 있어 초기 적재를 시작합니다", active);
                batchService.rebuildAsync("startup");
            }
        } catch (Exception e) {
            log.error("[RANKING] 초기 적재 판단 실패 — POST /admin/rankings/rebuild 로 수동 실행하세요", e);
        }
    }
}
