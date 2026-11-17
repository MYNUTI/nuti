package com.example.nutriuniv.domain.publicdata.entity;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 수집 회차 — API 하나를 처음부터 끝까지 한 번 받는 단위. 한도에 걸리면 멈췄다가 다음 날 이어 받는다(nextPage 부터).
 * <ul>
 *   <li>FULL: 전량. 끝나면 그 회차가 시작되기 전의 원본 행을 지운다</li>
 *   <li>INCREMENTAL: CHNG_DT 증분(최대 7일 전까지). 원본에 더해 쌓는다</li>
 * </ul>
 * API 하나에 RUNNING 회차는 하나만 둔다. 34컬럼 재계산은 API 마다 DONE 회차가 있고 RUNNING · FAILED 가 끼어 있지 않을 때만 돈다(스케줄러 단계).
 */
@Entity
@Table(name = "public_api_rounds",
        indexes = @Index(name = "idx_public_api_rounds_api_status", columnList = "api, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicApiRound {

    public enum Mode { FULL, INCREMENTAL }

    public enum Status { RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PublicApi 이름. enum 으로 매핑하지 않는다 — Hibernate 가 enum 칸에 CHECK 제약(값 목록)을 붙여, API 를 더하면 막힌다. */
    @Column(nullable = false, length = 20)
    private String api;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** CHNG_DT(yyyyMMdd) · EVL_YR(yyyy). 전량이면 null. */
    @Column(name = "condition_value", length = 10)
    private String conditionValue;

    @Column(name = "next_page", nullable = false)
    private int nextPage = 1;

    @Column(name = "total_count")
    private Long totalCount;

    @Column(name = "received_count", nullable = false)
    private long receivedCount;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "last_code", length = 30)
    private String lastCode;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static PublicApiRound start(PublicApi api, Mode mode, String conditionValue) {
        PublicApiRound r = new PublicApiRound();
        r.api = api.name();
        r.mode = mode;
        r.status = Status.RUNNING;
        r.conditionValue = conditionValue;
        r.startedAt = LocalDateTime.now();
        return r;
    }

    /** 한 페이지를 받았다. */
    public void received(int rows, Long total, String code, String message) {
        this.callCount++;
        this.receivedCount += rows;
        if (total != null && this.totalCount == null) this.totalCount = total;
        this.nextPage++;
        note(code, message);
    }

    /** 받지 못한 호출 — 페이지는 그대로(다음에 같은 페이지부터). */
    public void calledWithoutRows(String code, String message) {
        this.callCount++;
        note(code, message);
    }

    public void done(String message) {
        this.status = Status.DONE;
        this.finishedAt = LocalDateTime.now();
        if (message != null) this.lastMessage = cut(message);
    }

    public void fail(String code, String message) {
        this.status = Status.FAILED;
        this.finishedAt = LocalDateTime.now();
        note(code, message);
    }

    public PublicApi publicApi() {
        return PublicApi.valueOf(api);
    }

    public boolean running() {
        return status == Status.RUNNING;
    }

    private void note(String code, String message) {
        this.lastCode = code == null ? null : code.length() > 30 ? code.substring(0, 30) : code;
        this.lastMessage = cut(message);
    }

    private static String cut(String s) {
        return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s;
    }
}