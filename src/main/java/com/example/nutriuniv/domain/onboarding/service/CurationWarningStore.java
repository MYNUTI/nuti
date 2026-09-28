package com.example.nutriuniv.domain.onboarding.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 픽커가 대체를 발동했을 때 남기는 경고 (기능명세서 2.2 「관리자 화면에 경고를 남긴다」 → 10.1 경고 목록에 모인다).
 * 단일 인스턴스 전제의 메모리 보관(최근 20건). 재기동하면 사라진다 — 지속 보관이 필요하면 표로 옮긴다.
 */
@Component
public class CurationWarningStore {

    private static final int MAX = 20;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final Deque<String> recent = new ArrayDeque<>();

    public synchronized void add(String message) {
        String line = LocalDateTime.now().format(FMT) + " " + message;
        if (!recent.isEmpty() && recent.peekLast().endsWith(message)) return;   // 같은 경고 연속 중복 억제
        recent.addLast(line);
        while (recent.size() > MAX) recent.pollFirst();
    }

    public synchronized List<String> recent() {
        return new ArrayList<>(recent);
    }
}
