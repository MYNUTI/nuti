package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound.Mode;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound.Status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 언제 어떤 회차를 여는가 (sync.md 「주기」). 순수 함수 — CollectScheduleTest 로 고정.
 * <table>
 *   <tr><th>API</th><th>주기</th><th>방식</th></tr>
 *   <tr><td>C002 · C006 · I2852 · C005 · I2570</td><td>매일 19시 이후</td><td>증분 — CHNG_DT = 마지막으로 끝까지 받은 회차의 시작일</td></tr>
 *   <tr><td>같은 5개</td><td>30일마다 · 밤(19~06시)</td><td>전량. 마지막 성공이 7일보다 오래되면(증분 불가) 이것으로 복구</td></tr>
 *   <tr><td>I0490</td><td>매일</td><td>전량</td></tr>
 *   <tr><td>영양성분DB · 푸드QR 3종</td><td>7일마다</td><td>전량</td></tr>
 *   <tr><td>HACCP</td><td>30일마다</td><td>전량</td></tr>
 *   <tr><td>I0300 · I1420</td><td>해마다 · 밤</td><td>전량, EVL_YR = 작년</td></tr>
 * </table>
 * 받는 중인 회차가 있으면 열지 않는다. 전량이 실패하면 24시간 뒤에 다시 연다(같은 실패를 10분마다 되풀이하지 않게).
 * 식품안전나라 큰 전량(I0490 제외)은 밤에만 받는다 — CHNG_DT 없이 부르면 1시간 100회 제한이 있고 낮에는 300회 제한이 있다.
 */
public final class CollectSchedule {

    static final int NIGHT_FROM = 19, NIGHT_TO = 6;
    static final int CHNG_DT_FULL_DAYS = 30, CHNG_DT_MAX_DAYS = 7;
    static final int DATAGO_WEEKLY_DAYS = 7, HACCP_DAYS = 30;
    static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 회차 요약. */
    public record RoundInfo(Mode mode, Status status, String conditionValue, LocalDateTime startedAt) {}

    /**
     * @param latest       마지막 회차(무엇이든)
     * @param latestFull   마지막 전량 회차(무엇이든)
     * @param lastDone     마지막으로 끝까지 받은 회차(전량 · 증분)
     * @param lastFullDone 마지막으로 끝까지 받은 전량 회차
     */
    public record Snapshot(RoundInfo latest, RoundInfo latestFull, RoundInfo lastDone, RoundInfo lastFullDone) {
        public static final Snapshot EMPTY = new Snapshot(null, null, null, null);
    }

    /** 열 회차. condition 은 CHNG_DT(yyyyMMdd) · EVL_YR(yyyy) · null. */
    public record Start(Mode mode, String condition) {}

    private CollectSchedule() {}

    public static Optional<Start> decide(PublicApi api, LocalDateTime now, Snapshot s) {
        if (s.latest() != null && s.latest().status() == Status.RUNNING) return Optional.empty();
        boolean night = isNight(now);
        LocalDate today = now.toLocalDate();

        if (api.condition() == PublicApi.Condition.CHNG_DT) {
            if (fullDue(s, now, CHNG_DT_FULL_DAYS) && night && !recentFullFailure(s, now)) {
                return Optional.of(new Start(Mode.FULL, null));
            }
            if (now.getHour() < NIGHT_FROM) return Optional.empty();                         // 오늘 바뀐 자료는 19시 이후에 나온다
            if (s.latest() != null && s.latest().startedAt().toLocalDate().equals(today)) return Optional.empty();
            if (s.lastDone() == null) return Optional.empty();                               // 전량을 기다린다
            LocalDate since = s.lastDone().startedAt().toLocalDate();
            if (since.isBefore(today.minusDays(CHNG_DT_MAX_DAYS))) return Optional.empty();  // 증분 불가 — 전량을 기다린다
            return Optional.of(new Start(Mode.INCREMENTAL, since.format(YMD)));
        }
        if (api.condition() == PublicApi.Condition.EVL_YR) {
            String target = String.valueOf(today.getYear() - 1);
            boolean done = s.lastFullDone() != null && target.equals(s.lastFullDone().conditionValue());
            if (!done && night && !recentFullFailure(s, now)) return Optional.of(new Start(Mode.FULL, target));
            return Optional.empty();
        }
        if (api == PublicApi.I0490) {
            boolean startedToday = s.latest() != null && s.latest().startedAt().toLocalDate().equals(today);
            return startedToday ? Optional.empty() : Optional.of(new Start(Mode.FULL, null));
        }
        int days = api == PublicApi.HACCP ? HACCP_DAYS : DATAGO_WEEKLY_DAYS;
        if (fullDue(s, now, days) && !recentFullFailure(s, now)) return Optional.of(new Start(Mode.FULL, null));
        return Optional.empty();
    }

    /** 이 회차를 지금 받아도 되는가 — 식품안전나라 큰 전량은 밤에만. */
    public static boolean stepAllowed(PublicApi api, Mode mode, LocalDateTime now) {
        boolean bigFoodSafetyFull = api.provider() == PublicApi.Provider.FOOD_SAFETY && mode == Mode.FULL && api != PublicApi.I0490;
        return !bigFoodSafetyFull || isNight(now);
    }

    static boolean isNight(LocalDateTime now) {
        int h = now.getHour();
        return h >= NIGHT_FROM || h < NIGHT_TO;
    }

    private static boolean fullDue(Snapshot s, LocalDateTime now, int days) {
        return s.lastFullDone() == null || s.lastFullDone().startedAt().isBefore(now.minusDays(days));
    }

    private static boolean recentFullFailure(Snapshot s, LocalDateTime now) {
        RoundInfo f = s.latestFull();
        return f != null && f.status() == Status.FAILED && f.startedAt().isAfter(now.minusDays(1));
    }
}