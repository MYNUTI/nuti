package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.DefaultCalibration;
import com.example.nutriuniv.domain.grade.entity.*;
import com.example.nutriuniv.domain.grade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 현행 기준(Calibration) 제공·캐시·시드.
 * <ul>
 *   <li>{@link #current()} — APPLIED 최신 버전의 스냅샷. 요청마다 DB 를 읽지 않는다. 재산출 적용 후 {@link #refresh()}.</li>
 *   <li>{@link #ensureSeeded()} — 부팅 시 APPLIED 버전이 없으면 v1(구 PnsCalculator 상수)을 시드, 문구(grade_copies)도 비어 있으면 시드.</li>
 * </ul>
 * DB 에 아무 버전도 없으면(시드 직전 순간) 코드 기본값으로 계산해 응답이 끊기지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalibrationService {

    private final GradeRecalibrationRepository recalibrationRepository;
    private final GradeAnchorRepository anchorRepository;
    private final GradeCriterionRepository criterionRepository;
    private final GradeCutoffRepository cutoffRepository;
    private final GradeCopyRepository copyRepository;

    private volatile Calibration cached;

    // ── 조회 ──────────────────────────────────────────────────────────────────

    public Calibration current() {
        Calibration c = cached;
        if (c == null) {
            Optional<Calibration> loaded = loadApplied();
            if (loaded.isPresent()) {
                c = loaded.get();
                cached = c;
            } else {
                log.warn("[GRADE] 적용된 기준 버전이 없어 코드 기본값(v1)으로 계산합니다. 부팅 시드가 아직 안 돌았거나 실패했습니다.");
                c = DefaultCalibration.snapshot();
            }
        }
        return c;
    }

    public void refresh() {
        cached = loadApplied().orElse(null);
    }

    public Optional<Calibration> loadApplied() {
        return recalibrationRepository.findTopByStatusOrderByAppliedAtDesc(RecalibrationStatus.APPLIED)
                .map(this::build);
    }

    public Calibration load(Long recalibrationId) {
        GradeRecalibration rec = recalibrationRepository.findById(recalibrationId)
                .orElseThrow(() -> new IllegalStateException("기준 버전이 없습니다: " + recalibrationId));
        return build(rec);
    }

    /** 등급 라벨 — 현행 버전의 grade_cutoffs.label, 없으면 기본 문구. */
    public String label(GoalType goal, Grade grade) {
        if (grade == null) return null;
        String l = current().label(goal, grade);
        return l != null ? l : DefaultCalibration.defaultLabel(grade);
    }

    public String label(GoalType goal, String grade) {
        return label(goal, Grade.fromString(grade));
    }

    // ── 시드 ──────────────────────────────────────────────────────────────────

    @Transactional
    public void ensureSeeded() {
        if (recalibrationRepository.findTopByStatusOrderByAppliedAtDesc(RecalibrationStatus.APPLIED).isEmpty()) {
            GradeRecalibration v1 = recalibrationRepository.save(GradeRecalibration.applied(
                    DefaultCalibration.VERSION, LocalDate.now(), null, null, null, DefaultCalibration.MEMO, null));
            saveSet(v1.getId(), DefaultCalibration.anchors(), DefaultCalibration.rules(), DefaultCalibration.cutoffs());
            log.info("[GRADE] 초기 기준 {} 시드 완료 (id={})", v1.getVersion(), v1.getId());
        }
        if (copyRepository.count() == 0) {
            List<GradeCopy> copies = DefaultCalibration.copies().stream()
                    .map(s -> GradeCopy.create(s.code(), s.goal(), s.title(), s.body(), s.order(), DefaultCalibration.VERSION))
                    .toList();
            copyRepository.saveAll(copies);
            log.info("[GRADE] 등급 설명 문구 {}건 시드 완료", copies.size());
        }
        cached = null;
    }

    /** 한 버전의 앵커·기준값·컷오프 세트를 저장한다 (시드·재산출 공용). 호출자가 트랜잭션을 연다. */
    public void saveSet(Long recalibrationId,
                        Map<GoalType, Calibration.Anchor> anchors,
                        Map<GoalType, List<Calibration.Rule>> rules,
                        Map<GoalType, List<Calibration.Cutoff>> cutoffs) {
        List<GradeAnchor> anchorRows = new ArrayList<>();
        anchors.forEach((goal, a) -> anchorRows.add(GradeAnchor.create(recalibrationId, goal, bd(a.p1(), 3), bd(a.p99(), 3))));
        anchorRepository.saveAll(anchorRows);

        List<GradeCriterion> criterionRows = new ArrayList<>();
        rules.forEach((goal, list) -> list.forEach(r -> criterionRows.add(GradeCriterion.create(
                recalibrationId, goal, r.nutrient(), r.direction(),
                bd(r.weight(), 2), bd(r.threshold(), 3), r.maxValue() == null ? null : bd(r.maxValue(), 3),
                r.unit(), r.reasonTemplate()))));
        criterionRepository.saveAll(criterionRows);

        List<GradeCutoff> cutoffRows = new ArrayList<>();
        cutoffs.forEach((goal, list) -> list.forEach(c -> cutoffRows.add(GradeCutoff.create(
                recalibrationId, goal, c.grade(), bd(c.minScore(), 1), c.label()))));
        cutoffRepository.saveAll(cutoffRows);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private Calibration build(GradeRecalibration rec) {
        Map<GoalType, Calibration.Anchor> anchors = new EnumMap<>(GoalType.class);
        for (GradeAnchor a : anchorRepository.findByRecalibrationId(rec.getId())) {
            anchors.put(a.getGoal(), new Calibration.Anchor(a.getP1().doubleValue(), a.getP99().doubleValue()));
        }
        Map<GoalType, List<Calibration.Rule>> rules = new EnumMap<>(GoalType.class);
        for (GradeCriterion c : criterionRepository.findByRecalibrationIdAndIsActiveTrue(rec.getId())) {
            rules.computeIfAbsent(c.getGoal(), k -> new ArrayList<>()).add(new Calibration.Rule(
                    c.getNutrient(), c.getDirection(), c.getWeight().doubleValue(), c.getThreshold().doubleValue(),
                    c.getMaxValue() == null ? null : c.getMaxValue().doubleValue(), c.getUnit(), c.getReasonTemplate()));
        }
        Map<GoalType, List<Calibration.Cutoff>> cutoffs = new EnumMap<>(GoalType.class);
        for (GradeCutoff c : cutoffRepository.findByRecalibrationIdAndIsActiveTrue(rec.getId())) {
            cutoffs.computeIfAbsent(c.getGoal(), k -> new ArrayList<>())
                    .add(new Calibration.Cutoff(c.getGrade(), c.getMinScore().doubleValue(), c.getLabel()));
        }
        return new Calibration(rec.getId(), rec.getVersion(), rec.getAppliedAt(), anchors, rules, cutoffs);
    }

    private static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
