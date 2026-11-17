package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound.Mode;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound.Status;
import com.example.nutriuniv.domain.publicdata.service.CollectSchedule.RoundInfo;
import com.example.nutriuniv.domain.publicdata.service.CollectSchedule.Snapshot;
import com.example.nutriuniv.domain.publicdata.service.CollectSchedule.Start;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CollectScheduleTest {

    private static final LocalDateTime EVENING = LocalDateTime.of(2026, 11, 17, 19, 30);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 17, 12, 0);
    private static final LocalDateTime LATE_NIGHT = LocalDateTime.of(2026, 11, 17, 2, 0);

    private static RoundInfo done(Mode mode, LocalDateTime startedAt) {
        return new RoundInfo(mode, Status.DONE, null, startedAt);
    }

    /** 마지막이 전량 성공(시작 시각 at)인 상태. */
    private static Snapshot fullDoneAt(LocalDateTime at) {
        RoundInfo f = done(Mode.FULL, at);
        return new Snapshot(f, f, f, f);
    }

    // ── CHNG_DT 5개 ──

    @Test
    void 처음이면_밤에_전량() {
        assertThat(CollectSchedule.decide(PublicApi.C002, EVENING, Snapshot.EMPTY)).isEqualTo(Optional.of(new Start(Mode.FULL, null)));
        assertThat(CollectSchedule.decide(PublicApi.C002, NOON, Snapshot.EMPTY)).isEqualTo(Optional.empty());
    }

    @Test
    void 매일_19시_이후_증분_CHNG_DT는_마지막_성공_시작일() {
        Snapshot s = fullDoneAt(LocalDateTime.of(2026, 11, 14, 20, 0));
        assertThat(CollectSchedule.decide(PublicApi.C005, EVENING, s)).isEqualTo(Optional.of(new Start(Mode.INCREMENTAL, "20261114")));
        assertThat(CollectSchedule.decide(PublicApi.C005, NOON, s)).isEqualTo(Optional.empty());   // 19시 전
    }

    @Test
    void 오늘_이미_연_회차가_있으면_증분을_다시_열지_않는다() {
        RoundInfo full = done(Mode.FULL, LocalDateTime.of(2026, 11, 10, 20, 0));
        RoundInfo todayFailed = new RoundInfo(Mode.INCREMENTAL, Status.FAILED, "20261110", LocalDateTime.of(2026, 11, 17, 19, 10));
        Snapshot s = new Snapshot(todayFailed, full, full, full);
        assertThat(CollectSchedule.decide(PublicApi.C002, EVENING, s)).isEqualTo(Optional.empty());
    }

    @Test
    void 마지막_성공이_7일보다_오래되면_증분_대신_전량을_기다린다() {
        RoundInfo full = done(Mode.FULL, LocalDateTime.of(2026, 11, 1, 20, 0));
        RoundInfo inc = done(Mode.INCREMENTAL, LocalDateTime.of(2026, 11, 9, 19, 30));    // 8일 전
        Snapshot s = new Snapshot(inc, full, inc, full);
        assertThat(CollectSchedule.decide(PublicApi.I2852, EVENING, s)).isEqualTo(Optional.empty());
    }

    @Test
    void 전량이_30일_지나면_밤에_전량() {
        Snapshot s = fullDoneAt(LocalDateTime.of(2026, 10, 15, 20, 0));
        assertThat(CollectSchedule.decide(PublicApi.C006, EVENING, s)).isEqualTo(Optional.of(new Start(Mode.FULL, null)));
    }

    @Test
    void 전량이_실패하면_24시간_뒤에_다시() {
        RoundInfo failed = new RoundInfo(Mode.FULL, Status.FAILED, null, LocalDateTime.of(2026, 11, 17, 1, 0));
        Snapshot s = new Snapshot(failed, failed, null, null);
        assertThat(CollectSchedule.decide(PublicApi.C002, LATE_NIGHT, s)).isEqualTo(Optional.empty());
        assertThat(CollectSchedule.decide(PublicApi.C002, LocalDateTime.of(2026, 11, 18, 2, 0), s)).isEqualTo(Optional.of(new Start(Mode.FULL, null)));
    }

    @Test
    void 받는_중이면_열지_않는다() {
        RoundInfo running = new RoundInfo(Mode.FULL, Status.RUNNING, null, LocalDateTime.of(2026, 11, 16, 20, 0));
        Snapshot s = new Snapshot(running, running, null, null);
        for (PublicApi api : PublicApi.values()) {
            assertThat(CollectSchedule.decide(api, EVENING, s)).isEqualTo(Optional.empty());
        }
    }

    // ── 나머지 ──

    @Test
    void I0490은_하루_한_번_전량() {
        assertThat(CollectSchedule.decide(PublicApi.I0490, NOON, Snapshot.EMPTY)).isEqualTo(Optional.of(new Start(Mode.FULL, null)));
        Snapshot today = fullDoneAt(LocalDateTime.of(2026, 11, 17, 0, 10));
        assertThat(CollectSchedule.decide(PublicApi.I0490, NOON, today)).isEqualTo(Optional.empty());
    }

    @Test
    void 영양성분DB_푸드QR은_7일_HACCP은_30일마다_전량_낮에도() {
        Snapshot eightDays = fullDoneAt(LocalDateTime.of(2026, 11, 9, 11, 0));
        assertThat(CollectSchedule.decide(PublicApi.NUTRI_DB, NOON, eightDays)).isEqualTo(Optional.of(new Start(Mode.FULL, null)));
        assertThat(CollectSchedule.decide(PublicApi.FOODQR_NUTRITION, NOON, eightDays)).isEqualTo(Optional.of(new Start(Mode.FULL, null)));
        assertThat(CollectSchedule.decide(PublicApi.HACCP, NOON, eightDays)).isEqualTo(Optional.empty());
        Snapshot fiveDays = fullDoneAt(LocalDateTime.of(2026, 11, 12, 11, 0));
        assertThat(CollectSchedule.decide(PublicApi.NUTRI_DB, NOON, fiveDays)).isEqualTo(Optional.empty());
    }

    @Test
    void 생산실적은_해마다_작년_것을_밤에() {
        assertThat(CollectSchedule.decide(PublicApi.I0300, EVENING, Snapshot.EMPTY)).isEqualTo(Optional.of(new Start(Mode.FULL, "2025")));
        assertThat(CollectSchedule.decide(PublicApi.I0300, NOON, Snapshot.EMPTY)).isEqualTo(Optional.empty());
        RoundInfo y2025 = new RoundInfo(Mode.FULL, Status.DONE, "2025", LocalDateTime.of(2026, 1, 3, 20, 0));
        assertThat(CollectSchedule.decide(PublicApi.I1420, EVENING, new Snapshot(y2025, y2025, y2025, y2025))).isEqualTo(Optional.empty());
    }

    @Test
    void 식품안전나라_큰_전량은_밤에만_받는다() {
        assertThat(CollectSchedule.stepAllowed(PublicApi.C002, Mode.FULL, NOON)).isFalse();
        assertThat(CollectSchedule.stepAllowed(PublicApi.C002, Mode.FULL, LATE_NIGHT)).isTrue();
        assertThat(CollectSchedule.stepAllowed(PublicApi.C002, Mode.INCREMENTAL, NOON)).isTrue();
        assertThat(CollectSchedule.stepAllowed(PublicApi.I0490, Mode.FULL, NOON)).isTrue();
        assertThat(CollectSchedule.stepAllowed(PublicApi.NUTRI_DB, Mode.FULL, NOON)).isTrue();
    }
}