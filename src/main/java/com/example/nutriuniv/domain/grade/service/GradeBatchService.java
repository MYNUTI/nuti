package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.calc.GradeFormula;
import com.example.nutriuniv.domain.grade.dto.GradeBatchResult;
import com.example.nutriuniv.domain.grade.repository.GradeInputRepository;
import com.example.nutriuniv.domain.grade.repository.GradeInputRepository.GradeInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 등급 전량 재계산 — 분석 완료 제품 × 9슬롯을 계산해 product_grades 를 한 트랜잭션으로 교체한다.
 * <ul>
 *   <li>재계산 중 조회는 직전 값을 그대로 본다(4.2) — TRUNCATE 가 아니라 DELETE + INSERT 를 한 트랜잭션에서(MVCC).</li>
 *   <li>쓰기는 JDBC 배치(1,000행). 엔티티 saveAll 은 복합키 병합 SELECT 가 행마다 나가 수십만 행에 부적합.</li>
 *   <li>백분위는 구 배치와 같이 대분류(parent 없으면 자기 분류) 안에서 점수 내림차순.</li>
 * </ul>
 */
@Slf4j
@Service
public class GradeBatchService {

    private static final int BATCH_SIZE = 1_000;
    private static final String INSERT_SQL = """
            INSERT INTO product_grades
                (product_id, goal, eer_band, score, grade, percentile, top_penalty_nutrient, recalibration_id, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // 분석 완료 판정(4.1)으로 products.status 갱신 — 값이 바뀌는 행만 (WHERE 필수)
    private static final String STATUS_REFRESH_SQL = """
            UPDATE products p
            SET    status = s.new_status
            FROM  (SELECT p2.id,
                          CASE WHEN pn.product_id IS NOT NULL AND """ + GradeInputRepository.ANALYZED_NUTRIENT_PREDICATE + """
                               THEN 'ANALYZED' ELSE 'INSUFFICIENT' END AS new_status
                   FROM   products p2
                   LEFT JOIN product_nutrients pn ON pn.product_id = p2.id) s
            WHERE  s.id = p.id
              AND  p.status IS DISTINCT FROM s.new_status
            """;

    private final GradeInputRepository inputRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CalibrationService calibrationService;
    private final GradeEngineLock lock;
    private final TransactionTemplate txTemplate;

    public GradeBatchService(GradeInputRepository inputRepository, JdbcTemplate jdbcTemplate,
                             CalibrationService calibrationService, GradeEngineLock lock,
                             PlatformTransactionManager transactionManager) {
        this.inputRepository = inputRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.calibrationService = calibrationService;
        this.lock = lock;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 현행 기준으로 전량 재계산 (관리자 수동 실행·부팅 초기 적재). 다른 재계산·재산출이 진행 중이면 409. */
    public GradeBatchResult recomputeAll() {
        if (!lock.tryAcquire()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "등급 재계산 또는 기준 재산출이 이미 진행 중입니다.");
        }
        try {
            Calibration cal = calibrationService.current();
            return txTemplate.execute(status -> recomputeWith(cal));
        } finally {
            lock.release();
        }
    }

    /** 부팅 시 초기 적재용 — 서버 기동을 막지 않도록 별도 스레드. 실패해도 로그만 남긴다(다음 부팅·수동 실행에서 재시도). */
    public void recomputeAsync(String reason) {
        Thread t = new Thread(() -> {
            try {
                GradeBatchResult r = recomputeAll();
                log.info("[GRADE] 초기 적재 완료 ({}) — 제품 {}개 × {}슬롯 = {}행, {}ms, 기준 {}",
                        reason, r.productCount(), r.combinations(), r.savedRows(), r.elapsedMs(), r.version());
            } catch (Exception e) {
                log.error("[GRADE] 초기 적재 실패 ({}) — POST /admin/pns/calculate 로 다시 실행하세요", reason, e);
            }
        }, "grade-recompute");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 주어진 기준으로 전량 재계산. <b>호출자가 트랜잭션을 연다</b> — 재산출은 버전 저장과 같은 트랜잭션에서 부른다.
     */
    public GradeBatchResult recomputeWith(Calibration cal) {
        long startMs = System.currentTimeMillis();
        List<GradeInput> inputs = inputRepository.findAnalyzedInputs();
        log.info("[GRADE] 분석 완료 제품 {}개 로드 — 기준 {}", inputs.size(), cal.version());

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> rows = new ArrayList<>(inputs.size() * 9);
        int slotCount = 0;

        for (GoalType goal : GoalType.values()) {
            for (int band : EerBand.slots(goal)) {
                slotCount++;
                List<Scored> scored = new ArrayList<>(inputs.size());
                for (GradeInput in : inputs) {
                    scored.add(new Scored(in.productId(), in.groupCategoryId(),
                            GradeFormula.evaluate(cal, goal, band, in.input())));
                }
                // 대분류별 백분위
                Map<Long, List<Scored>> byGroup = scored.stream().collect(Collectors.groupingBy(Scored::groupId));
                for (List<Scored> group : byGroup.values()) {
                    group.sort(Comparator.comparingDouble((Scored s) -> s.result().score()).reversed()
                            .thenComparingLong(Scored::productId));
                    int total = group.size();
                    for (int i = 0; i < total; i++) {
                        Scored s = group.get(i);
                        BigDecimal percentile = BigDecimal.valueOf((double) (total - i) / total * 100.0)
                                .setScale(2, RoundingMode.HALF_UP);
                        rows.add(new Object[]{
                                s.productId(),
                                goal.name(),
                                band,
                                BigDecimal.valueOf(s.result().score()).setScale(1, RoundingMode.HALF_UP),
                                s.result().grade().name(),
                                percentile,
                                s.result().topPenaltyNutrient() == null ? null : s.result().topPenaltyNutrient().name(),
                                cal.recalibrationId(),
                                now
                        });
                    }
                }
            }
        }

        int deleted = jdbcTemplate.update("DELETE FROM product_grades");
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            jdbcTemplate.batchUpdate(INSERT_SQL, rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
        }
        int statusUpdated = refreshProductStatus();

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("[GRADE] 완료 — 기존 {}행 삭제, 제품 {}개 × {}슬롯 = {}행 저장, 제품 상태 {}건 갱신 ({}ms)",
                deleted, inputs.size(), slotCount, rows.size(), statusUpdated, elapsed);
        return new GradeBatchResult(inputs.size(), rows.size(), slotCount, elapsed, cal.version());
    }

    /**
     * 한 제품의 9슬롯만 현행 기준으로 다시 계산해 교체 (관리자 영양성분 수정 직후 — 기능명세서 10.4 「영양정보를 고치면 등급도 바뀐다」).
     * 백분위는 비워 둔다(다음 전량 배치가 채운다). input 이 null(영양정보 부족)이면 기존 슬롯 삭제만. <b>호출자가 트랜잭션을 연다.</b>
     * @return 목표별 조회 슬롯(EerBand.defaultSlot)의 등급 — 삭제만 했으면 빈 맵
     */
    public Map<GoalType, String> recomputeOne(long productId, GradeFormula.Input input) {
        jdbcTemplate.update("DELETE FROM product_grades WHERE product_id = ?", productId);
        Map<GoalType, String> defaults = new EnumMap<>(GoalType.class);
        if (input == null) return defaults;

        Calibration cal = calibrationService.current();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> rows = new ArrayList<>(9);
        for (GoalType goal : GoalType.values()) {
            for (int band : EerBand.slots(goal)) {
                GradeFormula.Result r = GradeFormula.evaluate(cal, goal, band, input);
                rows.add(new Object[]{
                        productId, goal.name(), band,
                        BigDecimal.valueOf(r.score()).setScale(1, RoundingMode.HALF_UP),
                        r.grade().name(), null,
                        r.topPenaltyNutrient() == null ? null : r.topPenaltyNutrient().name(),
                        cal.recalibrationId(), now
                });
                if (band == EerBand.defaultSlot(goal)) defaults.put(goal, r.grade().name());
            }
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows);
        return defaults;
    }

    /** products.status(분석 완료 여부) 갱신 — 바뀐 행 수. 호출자가 트랜잭션을 연다. */
    public int refreshProductStatus() {
        return jdbcTemplate.update(STATUS_REFRESH_SQL);
    }

    /** 부팅 시 백필용 (status 가 NULL 인 제품이 있을 때). */
    public int refreshProductStatusInTx() {
        Integer n = txTemplate.execute(status -> refreshProductStatus());
        return n == null ? 0 : n;
    }

    private record Scored(long productId, long groupId, GradeFormula.Result result) {}
}
