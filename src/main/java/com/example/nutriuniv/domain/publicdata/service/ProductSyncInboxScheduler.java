package com.example.nutriuniv.domain.publicdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CSV 수신함 자동 실행 — 주 1회, 매주 월요일 08:00(한국 시간)에 /data/csv 의 가장 새 파일을 반영한다.
 * 파이프라인은 밤사이 수집이 끝나면 07시 전후에 파일을 만든다. 한 주 사이에 파일이 여러 개 쌓여도 파일마다 전체 데이터라
 * 가장 새 파일 하나만 반영하고 나머지는 건너뛴다(SUPERSEDED). 새로 생긴 파일이 없으면 그 주는 건너뛴다.
 * 요일·시각은 product-sync.inbox.cron 으로 바꾼다. product-sync.inbox.enabled=false 면 끈다(수동 실행 API 는 그대로).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "product-sync.inbox.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSyncInboxScheduler {

    private final ProductSyncInboxService inboxService;

    @Scheduled(cron = "${product-sync.inbox.cron:0 0 8 * * MON}", zone = "Asia/Seoul")
    public void run() {
        try {
            inboxService.runScheduled();
        } catch (RuntimeException e) {
            log.error("[INBOX] 자동 실행 실패", e);
        }
    }
}