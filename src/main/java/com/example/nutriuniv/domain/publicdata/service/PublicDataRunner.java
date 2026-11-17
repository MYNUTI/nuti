package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import com.example.nutriuniv.domain.publicdata.dto.PublicApiRoundResponse;
import com.example.nutriuniv.domain.publicdata.dto.RecomputeResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncApplyResponse;
import com.example.nutriuniv.domain.publicdata.dto.SyncRunResponse;
import com.example.nutriuniv.domain.publicdata.dto.TickResponse;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;
import com.example.nutriuniv.domain.publicdata.repository.PublicApiRoundRepository;
import com.example.nutriuniv.domain.publicdata.service.CollectSchedule.RoundInfo;
import com.example.nutriuniv.domain.publicdata.service.CollectSchedule.Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 공공데이터 최신화를 한 바퀴씩 돌리는 곳 — 스케줄러(PublicDataScheduler)와 관리자 API 가 같이 쓴다.
 * <ul>
 *   <li>tick: 주기가 된 API 의 회차를 열고(CollectSchedule), 받는 중인 회차를 한도 안에서 이어 받는다</li>
 *   <li>syncRun: 재계산을 막는 이유가 없고 마지막 재계산 뒤 새로 끝난 회차가 있으면 재계산 → 반영</li>
 * </ul>
 * 같은 일이 겹쳐 돌지 않게 잠근다(스케줄러와 관리자 API 가 동시에 부를 수 있다). 서버 한 대 전제 — 두 대면 분산 락이 필요하다.
 */
@Slf4j
@Service
public class PublicDataRunner {

    /** 한 번에 한 회차를 이어 받는 최대 호출 수 — 실제로는 CallBudget 이 더 줄인다. */
    static final int MAX_CALLS_PER_STEP = 100;

    private final PublicApiCollector collector;
    private final PublicApiRoundRepository roundRepository;
    private final ProductRecomputeService recomputeService;
    private final ProductSyncApplyService applyService;
    private final JdbcTemplate jdbcTemplate;
    private final ReentrantLock tickLock = new ReentrantLock();
    private final ReentrantLock syncLock = new ReentrantLock();

    public PublicDataRunner(PublicApiCollector collector, PublicApiRoundRepository roundRepository,
                            ProductRecomputeService recomputeService, ProductSyncApplyService applyService,
                            JdbcTemplate jdbcTemplate) {
        this.collector = collector;
        this.roundRepository = roundRepository;
        this.recomputeService = recomputeService;
        this.applyService = applyService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── 수집 ─────────────────────────────────────────────────────────────────

    public TickResponse tick() {
        List<PublicApiRoundResponse> started = new ArrayList<>();
        List<PublicApiRoundResponse> stepped = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (!tickLock.tryLock()) {
            skipped.add("수집이 이미 돌고 있습니다");
            return new TickResponse(started, stepped, skipped, errors);
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            for (PublicApi api : PublicApi.values()) {
                try {
                    Optional<CollectSchedule.Start> plan = CollectSchedule.decide(api, now, snapshot(api));
                    if (plan.isPresent()) started.add(collector.start(api, plan.get().mode(), plan.get().condition()));
                } catch (RuntimeException e) {
                    errors.add(api + " 회차 열기 실패: " + e.getMessage());
                    log.warn("[SCHEDULE] {} 회차 열기 실패 — {}", api, e.getMessage());
                }
            }
            for (PublicApiRound r : roundRepository.findByStatusOrderByIdAsc(PublicApiRound.Status.RUNNING)) {
                PublicApi api = r.publicApi();
                if (!CollectSchedule.stepAllowed(api, r.getMode(), now)) {
                    skipped.add(api + " 전량(roundId=" + r.getId() + ")은 밤(19~06시)에만 받습니다");
                    continue;
                }
                try {
                    stepped.add(collector.step(r.getId(), MAX_CALLS_PER_STEP));
                } catch (RuntimeException e) {
                    errors.add(api + " 이어 받기 실패: " + e.getMessage());
                    log.warn("[SCHEDULE] {} 회차 {} 이어 받기 실패 — {}", api, r.getId(), e.getMessage());
                }
            }
        } finally {
            tickLock.unlock();
        }
        if (!started.isEmpty() || !errors.isEmpty()) {
            log.info("[SCHEDULE] 수집 — 연 회차 {} · 이어 받은 회차 {} · 오류 {}", started.size(), stepped.size(), errors.size());
        }
        return new TickResponse(started, stepped, skipped, errors);
    }

    Snapshot snapshot(PublicApi api) {
        String a = api.name();
        return new Snapshot(
                info(roundRepository.findTopByApiOrderByIdDesc(a)),
                info(roundRepository.findTopByApiAndModeOrderByIdDesc(a, PublicApiRound.Mode.FULL)),
                info(roundRepository.findTopByApiAndStatusOrderByIdDesc(a, PublicApiRound.Status.DONE)),
                info(roundRepository.findTopByApiAndModeAndStatusOrderByIdDesc(a, PublicApiRound.Mode.FULL, PublicApiRound.Status.DONE)));
    }

    private static RoundInfo info(Optional<PublicApiRound> r) {
        return r.map(x -> new RoundInfo(x.getMode(), x.getStatus(), x.getConditionValue(), x.getStartedAt())).orElse(null);
    }

    // ── 재계산 → 반영 ────────────────────────────────────────────────────────

    /**
     * @param force                 새로 끝난 회차가 없어도 돈다(관리자 수동 실행)
     * @param allowMassDeactivation 사라진 제품이 출처 적재 제품의 10%를 넘어도 비활성으로 돌린다
     */
    public SyncRunResponse syncRun(boolean force, boolean allowMassDeactivation) {
        if (!syncLock.tryLock()) return skipped(List.of("재계산·반영이 이미 돌고 있습니다"));
        try {
            List<String> blockers = recomputeService.blockers();
            if (!blockers.isEmpty()) return skipped(blockers);
            if (!force && !hasNewData()) return skipped(List.of("마지막 재계산 뒤 새로 끝난 회차가 없습니다"));

            RecomputeResponse recompute = recomputeService.recompute();
            SyncApplyResponse apply = applyService.apply(allowMassDeactivation);
            return new SyncRunResponse(true, List.of(), recompute, apply);
        } finally {
            syncLock.unlock();
        }
    }

    /** 마지막 재계산(복제 테이블 computed_at) 뒤에 끝까지 받은 회차가 있는가. 복제 테이블이 비었으면 true. */
    boolean hasNewData() {
        LocalDateTime computed = jdbcTemplate.queryForObject("SELECT MAX(computed_at) FROM product_staging", LocalDateTime.class);
        if (computed == null) return true;
        List<String> apis = ProductRecomputeService.REQUIRED.stream().map(Enum::name).toList();
        LocalDateTime finished = roundRepository.maxFinishedAt(PublicApiRound.Status.DONE, apis);
        return finished != null && finished.isAfter(computed);
    }

    private static SyncRunResponse skipped(List<String> reasons) {
        log.info("[SCHEDULE] 재계산·반영 건너뜀 — {}", reasons);
        return new SyncRunResponse(false, reasons, null, null);
    }

    // ── 정리 ─────────────────────────────────────────────────────────────────

    public int pruneCallLogs() {
        int n = collector.pruneCallLogs();
        if (n > 0) log.info("[SCHEDULE] 30일 지난 호출 기록 {}건 지움", n);
        return n;
    }
}