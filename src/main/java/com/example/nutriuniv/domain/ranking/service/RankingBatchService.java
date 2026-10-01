package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.ranking.entity.RankingCategory;
import com.example.nutriuniv.domain.ranking.repository.RankingBatchRepository;
import com.example.nutriuniv.domain.ranking.repository.RankingBatchRepository.CategoryStatRow;
import com.example.nutriuniv.domain.ranking.repository.RankingBatchRepository.RankRow;
import com.example.nutriuniv.domain.ranking.repository.RankingCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 랭킹 배치 (기능명세서 6.3 「매일 새벽 배치로 만들고 조회는 읽기만 한다」) — 매일 04:00 KST.
 * <ol>
 *   <li>서비스 분류별 분모·분석 완료·A/D 집계 → 게이트(300건 + A·D) 판정</li>
 *   <li>목표 3종 × 전 분류 순위를 창 함수로 한 번에 읽고(분류당 상위 max-rank), 분류 단위로 DELETE + INSERT (분류마다 한 트랜잭션)</li>
 *   <li>게이트 미통과 분류는 순위를 지우고 메타만 남긴다(대시보드가 이유를 보여준다)</li>
 * </ol>
 * 서비스 분류·매핑이 비어 있으면 아무 일도 하지 않는다 — 분류 체계는 팀 결정 대기. 매핑을 넣은 뒤 다음 새벽 또는
 * POST /admin/rankings/rebuild 로 채운다. 진행 중 중복 실행은 409.
 */
@Slf4j
@Service
public class RankingBatchService {

    public record RebuildResult(String trigger, int categories, int opened, int closed, int rows, long elapsedMs, LocalDateTime startedAt) {}

    private final RankingBatchRepository repository;
    private final RankingCategoryRepository categoryRepository;
    private final TransactionTemplate tx;
    private final int maxRank;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RankingBatchService(RankingBatchRepository repository,
                               RankingCategoryRepository categoryRepository,
                               PlatformTransactionManager transactionManager,
                               @Value("${app.ranking.max-rank:200}") int maxRank) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.maxRank = maxRank;
    }

    /** 매일 04:00 (KST). 실패해도 다음 날 다시 돈다 — 조회는 직전 결과를 계속 준다. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void nightly() {
        try {
            RebuildResult r = rebuild("schedule");
            log.info("[RANKING] 새벽 배치 완료 — 분류 {}개(열림 {}·닫힘 {}), {}행, {}ms", r.categories(), r.opened(), r.closed(), r.rows(), r.elapsedMs());
        } catch (Exception e) {
            log.error("[RANKING] 새벽 배치 실패 — 직전 순위를 계속 제공합니다. POST /admin/rankings/rebuild 로 재시도", e);
        }
    }

    /** 부팅 시(매핑은 있는데 결과가 없을 때) — 기동을 막지 않도록 별도 스레드. */
    public void rebuildAsync(String trigger) {
        Thread t = new Thread(() -> {
            try {
                RebuildResult r = rebuild(trigger);
                log.info("[RANKING] 초기 적재 완료 ({}) — 분류 {}개(열림 {}), {}행, {}ms", trigger, r.categories(), r.opened(), r.rows(), r.elapsedMs());
            } catch (Exception e) {
                log.error("[RANKING] 초기 적재 실패 ({}) — POST /admin/rankings/rebuild 로 다시 실행하세요", trigger, e);
            }
        }, "ranking-rebuild");
        t.setDaemon(true);
        t.start();
    }

    /** 전 분류 재계산. 관리자 수동 실행·스케줄·부팅이 공유한다. 진행 중이면 409. */
    public RebuildResult rebuild(String trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "랭킹 재계산이 이미 진행 중입니다.");
        }
        try {
            return doRebuild(trigger);
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private RebuildResult doRebuild(String trigger) {
        long startMs = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        List<RankingCategory> categories = categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();
        tx.executeWithoutResult(s -> repository.purgeInactive());
        if (categories.isEmpty()) {
            log.info("[RANKING] 서비스 분류가 없어 배치를 건너뜁니다 (분류 체계 결정 대기)");
            return new RebuildResult(trigger, 0, 0, 0, 0, System.currentTimeMillis() - startMs, now);
        }

        Map<Long, CategoryStatRow> stats = repository.categoryStats().stream()
                .collect(Collectors.toMap(CategoryStatRow::rankingCategoryId, r -> r));

        // 목표별 전 분류 순위 — 쿼리 3번
        Map<GoalType, Map<Long, List<RankRow>>> byGoal = new EnumMap<>(GoalType.class);
        for (GoalType goal : GoalType.values()) {
            Map<Long, List<RankRow>> grouped = repository.rankAll(goal, EerBand.defaultSlot(goal), maxRank).stream()
                    .collect(Collectors.groupingBy(RankRow::rankingCategoryId));
            byGoal.put(goal, grouped);
        }

        int opened = 0, closed = 0, rows = 0;
        for (RankingCategory c : categories) {
            CategoryStatRow stat = stats.getOrDefault(c.getId(), new CategoryStatRow(c.getId(), 0, 0, 0, 0));
            boolean passes = RankingGate.passes(stat.analyzedCount(), stat.aCount(), stat.dCount());
            int written = tx.execute(s -> {
                if (!passes) {
                    repository.deleteRankings(c.getId());
                    repository.upsertStat(c.getId(), stat, false, 0, now);
                    return 0;
                }
                int n = 0;
                for (GoalType goal : GoalType.values()) {
                    List<RankRow> list = byGoal.get(goal).getOrDefault(c.getId(), List.of());
                    n += repository.replaceRankings(c.getId(), goal, list, now);
                }
                repository.upsertStat(c.getId(), stat, true, n, now);
                return n;
            });
            if (passes) opened++; else closed++;
            rows += written;
            if (!passes) {
                log.info("[RANKING] 분류 「{}」 닫힘 — {}", c.getName(), RankingGate.failureReason(stat.analyzedCount(), stat.aCount(), stat.dCount()));
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("[RANKING] 재계산 ({}) — 분류 {}개, 열림 {}, 닫힘 {}, 순위 {}행, {}ms", trigger, categories.size(), opened, closed, rows, elapsed);
        return new RebuildResult(trigger, categories.size(), opened, closed, rows, elapsed, now);
    }
}
