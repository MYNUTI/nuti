package com.example.nutriuniv.domain.publicdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 최신화 스케줄러. <b>publicdata.scheduler.enabled=true 일 때만 켜진다</b> — 로컬 · 테스트 서버가 공공 API 한도를 같이 쓰지 않게.
 * <ul>
 *   <li>10분마다: 주기가 된 회차 열기 + 받는 중인 회차 이어 받기 (끝난 뒤 10분 쉬고 다음 — 겹쳐 돌지 않음)</li>
 *   <li>매일 05:40: 재계산 → 반영 (밤새 받은 것이 있을 때만)</li>
 *   <li>매일 04:10: 30일 지난 호출 기록 지우기</li>
 * </ul>
 * 시각은 한국 시간. 실패해도 다음 주기에 다시 돈다(예외는 로그만 남긴다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "publicdata.scheduler.enabled", havingValue = "true")
public class PublicDataScheduler {

    private static final String ZONE = "Asia/Seoul";

    private final PublicDataRunner runner;

    @Scheduled(fixedDelayString = "${publicdata.scheduler.tick-delay-ms:600000}", initialDelayString = "${publicdata.scheduler.initial-delay-ms:60000}")
    public void collect() {
        try {
            runner.tick();
        } catch (RuntimeException e) {
            log.error("[SCHEDULE] 수집 실패", e);
        }
    }

    @Scheduled(cron = "${publicdata.scheduler.sync-cron:0 40 5 * * *}", zone = ZONE)
    public void syncRun() {
        try {
            runner.syncRun(false, false);
        } catch (RuntimeException e) {
            log.error("[SCHEDULE] 재계산·반영 실패", e);
        }
    }

    @Scheduled(cron = "0 10 4 * * *", zone = ZONE)
    public void prune() {
        try {
            runner.pruneCallLogs();
        } catch (RuntimeException e) {
            log.error("[SCHEDULE] 호출 기록 정리 실패", e);
        }
    }
}