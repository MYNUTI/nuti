package com.example.nutriuniv.domain.publicdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CSV 수신함 자동 실행 — 매일 08:00(한국 시간)에 /data/csv 의 새 파일을 반영한다.
 * 파이프라인은 밤사이 수집이 끝나면 07시 전후에 파일을 만들고, 새로 끝난 수집이 없는 날은 만들지 않는다(그날은 건너뜀).
 * product-sync.inbox.enabled=false 면 끈다(수동 실행 API 는 그대로).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "product-sync.inbox.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSyncInboxScheduler {

    private final ProductSyncInboxService inboxService;

    @Scheduled(cron = "${product-sync.inbox.cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            inboxService.runScheduled();
        } catch (RuntimeException e) {
            log.error("[INBOX] 자동 실행 실패", e);
        }
    }
}
