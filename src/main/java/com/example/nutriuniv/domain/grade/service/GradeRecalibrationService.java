package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.calc.GradeFormula;
import com.example.nutriuniv.domain.grade.calc.Quantiles;
import com.example.nutriuniv.domain.grade.dto.RecalibrationHistoryResponse;
import com.example.nutriuniv.domain.grade.dto.RecalibrationPreviewResponse;
import com.example.nutriuniv.domain.grade.dto.RecalibrationResultResponse;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.GradeRecalibration;
import com.example.nutriuniv.domain.grade.repository.GradeInputRepository;
import com.example.nutriuniv.domain.grade.repository.GradeInputRepository.GradeInput;
import com.example.nutriuniv.domain.grade.repository.GradeRecalibrationRepository;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 등급 기준 재산출 (기능명세서 4.2·10.3).
 * <pre>
 *  전체 채점(raw) → 목표별 P1/P99 로 앵커 → 정규화 점수의 80/60/40/20% 지점을 A/B/C/D 컷오프 → 전체 등급 재계산
 * </pre>
 * <ul>
 *   <li>preview — 저장 없이 새 앵커·컷오프·전후 분포를 계산해 보여준다.</li>
 *   <li>execute — 새 버전 행 + 기준 세트(기준값은 현행 승계) + product_grades 전량 교체를 <b>한 트랜잭션</b>으로.
 *       실패하면 전부 되돌리고 ROLLED_BACK 기록만 남긴다 → 직전 버전 유지. 재계산 중 조회는 직전 값(MVCC).</li>
 *   <li>진행 중 중복 실행은 409. 「적재 완료 후에만」은 운영 규칙 — 서버가 적재 상태를 알 수 없어 관리자가 판단한다.</li>
 * </ul>
 */
@Slf4j
@Service
public class GradeRecalibrationService {

    private static final Grade[] QUANTILE_GRADES = {Grade.A, Grade.B, Grade.C, Grade.D};
    private static final double[] QUANTILE_POINTS = {0.80, 0.60, 0.40, 0.20};

    private final GradeInputRepository inputRepository;
    private final ProductGradeRepository productGradeRepository;
    private final GradeRecalibrationRepository recalibrationRepository;
    private final CalibrationService calibrationService;
    private final GradeBatchService batchService;
    private final GradeEngineLock lock;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate txNew;

    public GradeRecalibrationService(GradeInputRepository inputRepository,
                                     ProductGradeRepository productGradeRepository,
                                     GradeRecalibrationRepository recalibrationRepository,
                                     CalibrationService calibrationService,
                                     GradeBatchService batchService,
                                     GradeEngineLock lock,
                                     PlatformTransactionManager transactionManager) {
        this.inputRepository = inputRepository;
        this.productGradeRepository = productGradeRepository;
        this.recalibrationRepository = recalibrationRepository;
        this.calibrationService = calibrationService;
        this.batchService = batchService;
        this.lock = lock;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── 미리보기 ──────────────────────────────────────────────────────────────

    public RecalibrationPreviewResponse preview() {
        Calibration current = calibrationService.current();
        Plan plan = plan(current);
        return RecalibrationPreviewResponse.builder()
                .basedOnVersion(current.version())
                .targetCount(plan.targetCount())
                .anchors(toAnchorDto(plan.anchors()))
                .cutoffs(toCutoffDto(plan.cutoffs()))
                .beforeDistribution(plan.before())
                .afterDistribution(plan.after())
                .build();
    }

    // ── 실행 ──────────────────────────────────────────────────────────────────

    public RecalibrationResultResponse execute(Long adminUserId) {
        if (!lock.tryAcquire()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "등급 재계산 또는 기준 재산출이 이미 진행 중입니다.");
        }
        String version = nextVersion();
        try {
            GradeRecalibration applied = txTemplate.execute(status -> {
                Calibration current = calibrationService.current();
                Plan plan = plan(current);

                GradeRecalibration rec = recalibrationRepository.save(GradeRecalibration.applied(
                        version, LocalDate.now(), plan.targetCount(),
                        toJson(plan.before()), toJson(plan.after()),
                        "재산출: P1/P99 앵커 · 80/60/40/20% 컷오프 (" + current.version() + " 기준값 승계)",
                        adminUserId));
                calibrationService.saveSet(rec.getId(), plan.anchors(), current.rules(), plan.cutoffs());

                Calibration next = current.withRecalibrated(rec.getId(), rec.getVersion(), rec.getAppliedAt(),
                        plan.anchors(), plan.cutoffs());
                batchService.recomputeWith(next);
                return rec;
            });
            calibrationService.refresh();
            log.info("[GRADE] 기준 재산출 적용 — {} (모수 {}, 관리자 {})", applied.getVersion(), applied.getTargetCount(), adminUserId);
            return RecalibrationResultResponse.from(applied);

        } catch (CustomException e) {
            throw e;        // 모수 부족 등 사전 검증 — 아무것도 쓰지 않았다
        } catch (RuntimeException e) {
            log.error("[GRADE] 기준 재산출 실패 — 트랜잭션 되돌림, 직전 버전 유지 ({})", version, e);
            recordRollback(version, adminUserId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "기준 재산출에 실패해 직전 버전을 유지합니다: " + e.getMessage());
        } finally {
            lock.release();
        }
    }

    // ── 이력 ──────────────────────────────────────────────────────────────────

    public RecalibrationHistoryResponse history() {
        List<RecalibrationHistoryResponse.Item> items = recalibrationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(RecalibrationHistoryResponse.Item::from)
                .toList();
        return RecalibrationHistoryResponse.builder().items(items).build();
    }

    // ── 계산 ──────────────────────────────────────────────────────────────────

    /** 재산출 계획: 현행 기준값으로 채점 → 새 앵커·컷오프 → 전후 분포. 저장하지 않는다. */
    record Plan(int targetCount,
                Map<GoalType, Calibration.Anchor> anchors,
                Map<GoalType, List<Calibration.Cutoff>> cutoffs,
                Map<GoalType, Map<Grade, Long>> before,
                Map<GoalType, Map<Grade, Long>> after) {}

    Plan plan(Calibration current) {
        List<GradeInput> inputs = inputRepository.findAnalyzedInputs();
        if (inputs.isEmpty()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "분석 완료 제품이 없어 기준을 재산출할 수 없습니다.");
        }

        Map<GoalType, Calibration.Anchor> anchors = new EnumMap<>(GoalType.class);
        Map<GoalType, List<Calibration.Cutoff>> cutoffs = new EnumMap<>(GoalType.class);
        Map<GoalType, Map<Grade, Long>> after = new EnumMap<>(GoalType.class);

        for (GoalType goal : GoalType.values()) {
            int slot = EerBand.defaultSlot(goal);
            List<Calibration.Rule> rules = current.rules(goal);

            double[] raws = inputs.stream()
                    .mapToDouble(in -> GradeFormula.raw(goal, slot, in.input(), rules).raw())
                    .sorted()
                    .toArray();
            double p1  = GradeFormula.round3(Quantiles.linear(raws, 0.01));
            double p99 = GradeFormula.round3(Quantiles.linear(raws, 0.99));
            if (p99 - p1 < 1e-6) {
                throw new CustomException(ErrorCode.STATE_CONFLICT,
                        goal.label() + " 점수 분포가 퍼져 있지 않아(P1=P99) 앵커를 잡을 수 없습니다.");
            }
            Calibration.Anchor anchor = new Calibration.Anchor(p1, p99);

            double[] scores = Arrays.stream(raws).map(r -> GradeFormula.normalize(r, anchor)).sorted().toArray();
            List<Calibration.Cutoff> cut = new ArrayList<>(5);
            for (int i = 0; i < QUANTILE_GRADES.length; i++) {
                Grade g = QUANTILE_GRADES[i];
                cut.add(new Calibration.Cutoff(g, GradeFormula.round1(Quantiles.linear(scores, QUANTILE_POINTS[i])),
                        labelOf(current, goal, g)));
            }
            cut.add(new Calibration.Cutoff(Grade.E, 0.0, labelOf(current, goal, Grade.E)));
            List<Calibration.Cutoff> sorted = cut.stream()
                    .sorted(Comparator.comparingDouble(Calibration.Cutoff::minScore).reversed()).toList();

            Map<Grade, Long> dist = emptyDistribution();
            for (double s : scores) {
                dist.merge(Calibration.gradeOf(sorted, s), 1L, Long::sum);
            }

            anchors.put(goal, anchor);
            cutoffs.put(goal, sorted);
            after.put(goal, dist);
        }

        return new Plan(inputs.size(), anchors, cutoffs, beforeDistribution(), after);
    }

    private Map<GoalType, Map<Grade, Long>> beforeDistribution() {
        Map<GoalType, Map<Grade, Long>> before = new EnumMap<>(GoalType.class);
        for (GoalType goal : GoalType.values()) {
            before.put(goal, emptyDistribution());
        }
        for (Object[] row : productGradeRepository.countByGoalAndGradeAtSlot(
                GoalType.GENERAL, EerBand.GENERAL_SLOT, EerBand.DEFAULT.kcal())) {
            GoalType goal = (GoalType) row[0];
            Grade grade = (Grade) row[1];
            long count = ((Number) row[2]).longValue();
            before.get(goal).put(grade, count);
        }
        return before;
    }

    private static Map<Grade, Long> emptyDistribution() {
        Map<Grade, Long> m = new EnumMap<>(Grade.class);
        for (Grade g : Grade.values()) m.put(g, 0L);
        return m;
    }

    private static String labelOf(Calibration current, GoalType goal, Grade grade) {
        String l = current.label(goal, grade);
        return l != null ? l : com.example.nutriuniv.domain.grade.calc.DefaultCalibration.defaultLabel(grade);
    }

    private String nextVersion() {
        long n = recalibrationRepository.count() + 1;
        while (recalibrationRepository.existsByVersion("v" + n)) n++;
        return "v" + n;
    }

    private void recordRollback(String version, Long adminUserId, Exception cause) {
        try {
            String msg = "실패(트랜잭션 되돌림): " + String.valueOf(cause.getMessage());
            String memo = msg.length() > 500 ? msg.substring(0, 500) : msg;
            txNew.executeWithoutResult(status ->
                    recalibrationRepository.save(GradeRecalibration.rolledBack(version, LocalDate.now(), memo, adminUserId)));
        } catch (Exception e) {
            log.warn("[GRADE] 재산출 실패 기록 저장도 실패했습니다", e);
        }
    }

    // ── 변환 ──────────────────────────────────────────────────────────────────

    private static Map<GoalType, RecalibrationPreviewResponse.AnchorDto> toAnchorDto(Map<GoalType, Calibration.Anchor> anchors) {
        Map<GoalType, RecalibrationPreviewResponse.AnchorDto> m = new EnumMap<>(GoalType.class);
        anchors.forEach((g, a) -> m.put(g, RecalibrationPreviewResponse.AnchorDto.builder()
                .p1(bd(a.p1(), 3)).p99(bd(a.p99(), 3)).build()));
        return m;
    }

    private static Map<GoalType, Map<Grade, BigDecimal>> toCutoffDto(Map<GoalType, List<Calibration.Cutoff>> cutoffs) {
        Map<GoalType, Map<Grade, BigDecimal>> m = new EnumMap<>(GoalType.class);
        cutoffs.forEach((g, list) -> {
            Map<Grade, BigDecimal> byGrade = new EnumMap<>(Grade.class);
            list.forEach(c -> byGrade.put(c.grade(), bd(c.minScore(), 1)));
            m.put(g, byGrade);
        });
        return m;
    }

    /** {"WEIGHT_LOSS":{"A":12,"B":34,…},…} — 감사용 텍스트 컬럼. 라이브러리 의존 없이 조립한다. */
    static String toJson(Map<GoalType, Map<Grade, Long>> dist) {
        StringBuilder sb = new StringBuilder("{");
        boolean firstGoal = true;
        for (Map.Entry<GoalType, Map<Grade, Long>> e : dist.entrySet()) {
            if (!firstGoal) sb.append(',');
            firstGoal = false;
            sb.append('"').append(e.getKey().name()).append("\":{");
            boolean firstGrade = true;
            for (Map.Entry<Grade, Long> g : e.getValue().entrySet()) {
                if (!firstGrade) sb.append(',');
                firstGrade = false;
                sb.append('"').append(g.getKey().name()).append("\":").append(g.getValue());
            }
            sb.append('}');
        }
        return sb.append('}').toString();
    }

    private static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
