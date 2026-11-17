package com.example.nutriuniv.domain.search.service;

import com.example.nutriuniv.domain.search.dto.PopularKeywordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 인기 검색어 (v3 SRCH_009 · 11-05). 검색 로그(search_logs)를 매시 정각에 모아 메모리에 둔다.
 * <ul>
 *   <li>인기 = 최근 {@value #WINDOW_DAYS}일 동안 그 말을 검색한 사람 수(계정 → 기기 → 세션 순으로 한 사람). 같은 사람이 여러 번 찾아도 1</li>
 *   <li>대소문자 · 앞뒤 공백만 다른 말은 하나로 묶고, 가장 많이 쓴 모양으로 보인다</li>
 *   <li>결과가 0건이던 검색은 세지 않는다(눌러도 결과가 없는 말이 오르지 않게)</li>
 *   <li>{@value #MIN_SEARCHERS}명 미만이 찾은 말은 올리지 않는다(한 사람의 검색이 남에게 보이지 않게)</li>
 *   <li>급상승 검색어는 출시 때 숨긴다(11-11) — 여기서 만들지 않는다</li>
 * </ul>
 * 셀 기간 · 최소 검색 수 · 갱신 주기는 명세에서 결정 대기라 상수로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularKeywordService {

    static final int TOP_N = 10;
    static final int WINDOW_DAYS = 7;
    static final int MIN_SEARCHERS = 3;

    private static final String SQL = """
            SELECT MODE() WITHIN GROUP (ORDER BY TRIM(s.keyword)) AS keyword
            FROM search_logs s
            WHERE s.searched_at >= ? AND s.searched_at < ? AND s.result_count > 0 AND TRIM(s.keyword) <> ''
            GROUP BY LOWER(TRIM(s.keyword))
            HAVING COUNT(DISTINCT COALESCE('u' || s.user_id, 'a' || s.anonymous_id, 's' || s.session_id)) >= ?
            ORDER BY COUNT(DISTINCT COALESCE('u' || s.user_id, 'a' || s.anonymous_id, 's' || s.session_id)) DESC,
                     COUNT(*) DESC, LOWER(TRIM(s.keyword))
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    private volatile PopularKeywordResponse current = PopularKeywordResponse.empty();

    public PopularKeywordResponse popular() {
        return current;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        refresh();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void refresh() {
        LocalDateTime basedAt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        try {
            current = compute(basedAt);
            log.info("[SEARCH] 인기 검색어 갱신 — {}개 ({} 기준)", current.keywords().size(), basedAt);
        } catch (Exception e) {
            log.error("[SEARCH] 인기 검색어 갱신 실패 — 이전 값 유지: {}", e.getMessage());
        }
    }

    PopularKeywordResponse compute(LocalDateTime basedAt) {
        List<String> words = jdbcTemplate.queryForList(SQL, String.class,
                Timestamp.valueOf(basedAt.minusDays(WINDOW_DAYS)), Timestamp.valueOf(basedAt), MIN_SEARCHERS, TOP_N);
        List<PopularKeywordResponse.Item> items = new ArrayList<>();
        for (String w : words) items.add(new PopularKeywordResponse.Item(items.size() + 1, w));
        return new PopularKeywordResponse(basedAt, List.copyOf(items));
    }
}
