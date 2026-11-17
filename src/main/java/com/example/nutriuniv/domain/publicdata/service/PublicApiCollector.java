package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.publicdata.client.ApiPage;
import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import com.example.nutriuniv.domain.publicdata.client.PublicApiClient;
import com.example.nutriuniv.domain.publicdata.dto.PublicApiRoundResponse;
import com.example.nutriuniv.domain.publicdata.dto.PublicApiStatusResponse;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;
import com.example.nutriuniv.domain.publicdata.repository.PublicApiCallLogRepository;
import com.example.nutriuniv.domain.publicdata.repository.PublicApiRawRepository;
import com.example.nutriuniv.domain.publicdata.repository.PublicApiRoundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 공공 API 수집 (공공데이터 최신화 1단계) — API 응답을 원본 표(public_api_raw)에 그대로 쌓는다.
 * <ul>
 *   <li>회차(PublicApiRound) 단위로 받는다. start 로 회차를 열고, step 으로 한도 안에서 몇 페이지씩 받는다 — 한도에 걸리면 멈췄다가 다음 step 에서 이어 받는다</li>
 *   <li>페이지마다 따로 커밋한다 — 중간에 서버가 꺼져도 받은 페이지까지는 남고 nextPage 부터 이어 받는다</li>
 *   <li>끝까지 받았는지는 RoundProgress 가 본다. 행 수가 맞지 않으면 실패로 돌리고 그 회차 행을 지운다(일부만 받은 채 계산하지 않게)</li>
 *   <li>증분 회차의 전체 행 수가 비정상적으로 크면 조건이 무시된 것으로 보고 실패(조건 이름을 틀리면 오류 없이 전체 목록이 온다)</li>
 *   <li>전량 회차가 끝나면 그 회차 전에 시작한 회차들의 행을 지운다(원천에서 삭제된 줄을 걸러내는 유일한 방법)</li>
 * </ul>
 * 언제 무엇을 부를지(주기)는 스케줄러가 정한다. 여기서는 관리자 API 로 손으로도 돌릴 수 있다.
 */
@Slf4j
@Service
public class PublicApiCollector {

    /** 증분 회차인데 전체 행 수가 이보다 크면 조건이 무시된 것으로 본다. */
    static final long MAX_INCREMENTAL_TOTAL = 100_000;
    /** CHNG_DT 는 호출일 기준 최대 7일 전까지(ERROR-303). */
    static final int CHNG_DT_MAX_DAYS = 7;
    static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PublicApiRoundRepository roundRepository;
    private final PublicApiRawRepository rawRepository;
    private final PublicApiCallLogRepository callLogRepository;
    private final Map<PublicApi.Provider, PublicApiClient> clients = new EnumMap<>(PublicApi.Provider.class);
    private final TransactionTemplate txTemplate;
    private final int dataGoKrDailyLimit;

    public PublicApiCollector(PublicApiRoundRepository roundRepository, PublicApiRawRepository rawRepository,
                              PublicApiCallLogRepository callLogRepository, List<PublicApiClient> clientList,
                              PlatformTransactionManager transactionManager,
                              @Value("${publicdata.datagokr.daily-limit:1000}") int dataGoKrDailyLimit) {
        this.roundRepository = roundRepository;
        this.rawRepository = rawRepository;
        this.callLogRepository = callLogRepository;
        for (PublicApiClient c : clientList) clients.put(c.provider(), c);
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.dataGoKrDailyLimit = dataGoKrDailyLimit;
    }

    // ── 회차 열기 ─────────────────────────────────────────────────────────────

    /**
     * @param condition INCREMENTAL 이면 CHNG_DT(yyyyMMdd, 최대 7일 전). EVL_YR 서비스(I0300 · I1420)의 FULL 이면 연도(yyyy). 그 밖에는 무시
     */
    public PublicApiRoundResponse start(PublicApi api, PublicApiRound.Mode mode, String condition) {
        String value = validateCondition(api, mode, condition, LocalDate.now());
        roundRepository.findFirstByApiAndStatus(api.name(), PublicApiRound.Status.RUNNING).ifPresent(r -> {
            throw new CustomException(ErrorCode.STATE_CONFLICT,
                    api + " 은 이미 받는 중인 회차가 있습니다(roundId=" + r.getId() + "). 이어 받거나 취소한 뒤 여세요.");
        });
        PublicApiRound round = roundRepository.save(PublicApiRound.start(api, mode, value));
        log.info("[COLLECT] {} {} 회차 시작 id={} 조건={}", api, mode, round.getId(), value);
        return PublicApiRoundResponse.from(round);
    }

    /** 조건 값 검사 — 저장할 값을 돌려준다. 순수 함수(테스트용으로 열어 둠). */
    static String validateCondition(PublicApi api, PublicApiRound.Mode mode, String condition, LocalDate today) {
        String c = condition == null ? "" : condition.trim();
        if (mode == PublicApiRound.Mode.INCREMENTAL) {
            if (api.condition() != PublicApi.Condition.CHNG_DT) {
                throw new CustomException(ErrorCode.BAD_REQUEST, api + " 은 증분(CHNG_DT)을 지원하지 않습니다. 전량으로 받으세요.");
            }
            LocalDate date;
            try {
                date = LocalDate.parse(c, YMD);
            } catch (DateTimeParseException e) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "증분 조건은 yyyyMMdd 입니다: " + c);
            }
            if (date.isAfter(today) || date.isBefore(today.minusDays(CHNG_DT_MAX_DAYS))) {
                throw new CustomException(ErrorCode.BAD_REQUEST,
                        "CHNG_DT 는 오늘부터 " + CHNG_DT_MAX_DAYS + "일 전까지만 됩니다. 그보다 오래 멈췄으면 전량으로 받으세요.");
            }
            return c;
        }
        if (api.condition() == PublicApi.Condition.EVL_YR) {
            if (!c.matches("\\d{4}") || Integer.parseInt(c) > Year.now().getValue()) {
                throw new CustomException(ErrorCode.BAD_REQUEST, api + " 은 연도(EVL_YR, yyyy)가 필요합니다: " + c);
            }
            return c;
        }
        return null;
    }

    // ── 받기 ─────────────────────────────────────────────────────────────────

    /** 회차를 이어 받는다 — 한도 안에서 최대 maxCalls 번. 한도 초과 · 서버 오류면 멈추고 회차는 RUNNING 으로 남는다. */
    public PublicApiRoundResponse step(long roundId, int maxCalls) {
        PublicApiRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 회차입니다: " + roundId));
        if (!round.running()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "이미 끝난 회차입니다(" + round.getStatus() + ").");
        }
        PublicApi api = round.publicApi();
        PublicApiClient client = clients.get(api.provider());
        boolean usingChngDt = round.getMode() == PublicApiRound.Mode.INCREMENTAL;

        int allowed = CallBudget.allowed(api, dailyLimit(api), usingChngDt, LocalDateTime.now(), used(api), maxCalls);
        if (allowed == 0) {
            log.info("[COLLECT] {} 회차 {} — 지금은 호출 한도가 남지 않았습니다", api, roundId);
        }
        for (int i = 0; i < allowed && round.running(); i++) {
            ApiPage page = client.fetch(api, round.getNextPage(), round.getConditionValue());
            LocalDateTime now = LocalDateTime.now();
            callLogRepository.log(api, page.code(), now);
            boolean keepGoing = handle(round, page, now);
            if (!keepGoing) break;
        }
        return PublicApiRoundResponse.from(round);
    }

    /** 한 페이지 결과 처리. 다음 페이지를 계속 불러도 되면 true. */
    private boolean handle(PublicApiRound round, ApiPage page, LocalDateTime now) {
        PublicApi api = round.publicApi();
        switch (page.status()) {
            case OK, NO_DATA -> {
                boolean firstPage = round.getNextPage() == 1;
                if (round.getMode() == PublicApiRound.Mode.INCREMENTAL && firstPage
                        && page.totalCount() != null && page.totalCount() > MAX_INCREMENTAL_TOTAL) {
                    failAndClean(round, "CONDITION_IGNORED", "증분인데 전체 " + page.totalCount() + "행 — 조건이 무시된 것으로 봅니다");
                    return false;
                }
                txTemplate.executeWithoutResult(s -> {
                    rawRepository.insert(api, round.getId(), page.rows(), now);
                    round.received(page.rows().size(), page.totalCount(), page.code(), page.message());
                    switch (RoundProgress.after(api, round.getTotalCount(), round.getReceivedCount(), page.rows().size())) {
                        case DONE -> {
                            if (round.getMode() == PublicApiRound.Mode.FULL && round.getReceivedCount() == 0) {
                                // 전량인데 0행 — 이대로 끝내면 이전 원본을 다 지운다. 원천 장애로 본다
                                round.fail("EMPTY_FULL", "전량 회차인데 받은 행이 0개 — 이전 원본을 지우지 않습니다");
                                log.warn("[COLLECT] {} 전량 회차 {} 0행으로 실패", api, round.getId());
                                roundRepository.save(round);
                                return;
                            }
                            round.done("받은 " + round.getReceivedCount() + "행");
                            if (round.getMode() == PublicApiRound.Mode.FULL) {
                                int removed = rawRepository.deleteRowsBefore(api, round.getId(), round.getStartedAt());
                                log.info("[COLLECT] {} 전량 회차 {} 끝 — {}행, 이전 행 {}개 지움", api, round.getId(), round.getReceivedCount(), removed);
                            } else {
                                log.info("[COLLECT] {} 증분 회차 {} 끝 — {}행", api, round.getId(), round.getReceivedCount());
                            }
                        }
                        case COUNT_MISMATCH -> {
                            round.fail("COUNT_MISMATCH", "받은 " + round.getReceivedCount() + "행 / 전체 " + round.getTotalCount() + "행");
                            rawRepository.deleteRound(round.getId());
                            log.warn("[COLLECT] {} 회차 {} 행 수 불일치로 실패 — {}", api, round.getId(), round.getLastMessage());
                        }
                        case CONTINUE -> { }
                    }
                    roundRepository.save(round);
                });
                return round.running();
            }
            case QUOTA_EXCEEDED, RETRY_LATER -> {
                round.calledWithoutRows(page.code(), page.message());
                roundRepository.save(round);
                log.info("[COLLECT] {} 회차 {} 멈춤 ({} {}) — 다음에 {}페이지부터 이어 받습니다",
                        api, round.getId(), page.code(), page.message(), round.getNextPage());
                return false;
            }
            default -> {   // KEY_REJECTED, ERROR
                failAndClean(round, page.code(), page.message());
                return false;
            }
        }
    }

    private void failAndClean(PublicApiRound round, String code, String message) {
        txTemplate.executeWithoutResult(s -> {
            round.fail(code, message);
            rawRepository.deleteRound(round.getId());
            roundRepository.save(round);
        });
        log.warn("[COLLECT] {} 회차 {} 실패 — {} {}", round.getApi(), round.getId(), code, message);
    }

    /** 받는 중인 회차를 취소한다 — 받은 행도 지운다. */
    public PublicApiRoundResponse cancel(long roundId) {
        PublicApiRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 회차입니다: " + roundId));
        if (!round.running()) throw new CustomException(ErrorCode.STATE_CONFLICT, "이미 끝난 회차입니다(" + round.getStatus() + ").");
        failAndClean(round, "CANCELLED", "관리자가 취소");
        return PublicApiRoundResponse.from(round);
    }

    // ── 상태 ─────────────────────────────────────────────────────────────────

    public List<PublicApiStatusResponse> status() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        List<PublicApiStatusResponse> out = new ArrayList<>();
        for (PublicApi api : PublicApi.values()) {
            PublicApiRound latest = roundRepository.findTopByApiOrderByIdDesc(api.name()).orElse(null);
            LocalDateTime lastDone = roundRepository.findTopByApiAndStatusOrderByIdDesc(api.name(), PublicApiRound.Status.DONE)
                    .map(PublicApiRound::getFinishedAt).orElse(null);
            LocalDateTime lastFull = roundRepository.findTopByApiAndModeAndStatusOrderByIdDesc(api.name(), PublicApiRound.Mode.FULL, PublicApiRound.Status.DONE)
                    .map(PublicApiRound::getFinishedAt).orElse(null);
            out.add(new PublicApiStatusResponse(api.name(), api.label(), api.provider().name(), api.condition().name(),
                    PublicApiRoundResponse.from(latest), lastDone, lastFull, rawRepository.count(api),
                    callLogRepository.count(api, todayStart, now.plusSeconds(1)), dailyLimit(api)));
        }
        return out;
    }

    /** 30일 지난 호출 기록 지우기 — 스케줄러가 부른다. */
    public int pruneCallLogs() {
        return callLogRepository.deleteBefore(LocalDateTime.now().minusDays(30));
    }

    // ── 한도 ─────────────────────────────────────────────────────────────────

    int dailyLimit(PublicApi api) {
        return api.provider() == PublicApi.Provider.DATA_GO_KR ? dataGoKrDailyLimit : api.dailyLimit();
    }

    private CallBudget.Used used(PublicApi api) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now.plusSeconds(1);
        LocalDate today = now.toLocalDate();
        return new CallBudget.Used(
                callLogRepository.count(api, today.atStartOfDay(), end),
                callLogRepository.count(api, now.minusHours(1), end),
                callLogRepository.count(api, today.atTime(CallBudget.DAYTIME_FROM, 0), today.atTime(CallBudget.DAYTIME_TO, 0)));
    }
}