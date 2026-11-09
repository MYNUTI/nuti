package com.example.nutriuniv.domain.logging.service;

import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.logging.entity.VisitSession;
import com.example.nutriuniv.domain.logging.repository.VisitSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 방문 세션 추적 — 모든 로깅 이벤트 진입 시 호출되어 visit_sessions를 upsert 한다.
 *
 * <p>이게 재방문(return_visit) 측정의 실제 구현부다.
 * 세션 단위로 첫 방문/재방문, 유효 방문 여부, 며칠째(days_since_first)를 누적한다.
 * 식별자(anonymousId/sessionId)가 없으면(=클라가 헤더 미전송) 조용히 skip 한다.
 *
 * <p>실패를 여기서 삼키지 않는다 — LoggingService 가 LogWriter(별도 트랜잭션)로 감싸 부르므로,
 * 세션 추적이 실패해도 이벤트 로그와 요청은 그대로 간다.
 */
@Service
@RequiredArgsConstructor
public class VisitTrackingService {

    private final VisitSessionRepository visitSessionRepository;

    /**
     * @param qualifying 유효 방문(search/view) 여부. cta/filter는 false.
     */
    @Transactional
    public void track(LogContext ctx, boolean qualifying) {
        if (ctx.anonymousId() == null || ctx.anonymousId().isBlank()
                || ctx.sessionId() == null || ctx.sessionId().isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        VisitSession session = visitSessionRepository.findBySessionId(ctx.sessionId()).orElse(null);

        if (session == null) {
            LocalDateTime firstStart = visitSessionRepository.findFirstSessionStart(ctx.anonymousId());
            boolean isFirst = (firstStart == null);
            int daysSinceFirst = isFirst
                    ? 0
                    : (int) ChronoUnit.DAYS.between(firstStart.toLocalDate(), now.toLocalDate());
            session = VisitSession.create(ctx, now, isFirst, daysSinceFirst);
        }

        session.touch(now, qualifying, ctx.userId());
        visitSessionRepository.save(session);
    }
}
