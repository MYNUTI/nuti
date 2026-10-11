package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.CriterionDirection;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.Nutrient;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 한 기준 버전(grade_recalibrations 1행 + anchors·criteria·cutoffs)의 메모리 스냅샷. 불변.
 * 배치·조회는 이 객체만 보고 계산한다 — DB 를 매 계산마다 읽지 않는다 (CalibrationService 가 캐시).
 *
 * @param recalibrationId null 이면 DB 에 아직 시드되지 않은 코드 기본값(DefaultCalibration)
 */
public record Calibration(
        Long recalibrationId,
        String version,
        LocalDateTime appliedAt,
        Map<GoalType, Anchor> anchors,
        Map<GoalType, List<Rule>> rules,
        Map<GoalType, List<Cutoff>> cutoffs
) {

    /** 정규화 앵커: raw P1 → 0점, P99 → 100점. */
    public record Anchor(double p1, double p99) {}

    /** 성분별 기준값 한 줄 (grade_criteria). maxValue 는 PENALTY 의 최대 감점 도달값 / CALORIE 의 상한 M(null = 열량구간별). */
    public record Rule(Nutrient nutrient, CriterionDirection direction, double weight,
                       double threshold, Double maxValue, String unit, String reasonTemplate) {}

    /** 등급 컷오프 한 줄 (grade_cutoffs). score ≥ minScore 인 가장 높은 등급. */
    public record Cutoff(Grade grade, double minScore, String label) {}

    public Calibration {
        anchors = Map.copyOf(anchors);
        Map<GoalType, List<Rule>> r = new EnumMap<>(GoalType.class);
        rules.forEach((g, list) -> r.put(g, List.copyOf(list)));
        rules = Map.copyOf(r);
        Map<GoalType, List<Cutoff>> c = new EnumMap<>(GoalType.class);
        cutoffs.forEach((g, list) -> c.put(g, sortDesc(list)));
        cutoffs = Map.copyOf(c);
    }

    public Anchor anchor(GoalType goal) {
        Anchor a = anchors.get(goal);
        if (a == null) throw new IllegalStateException("앵커가 없는 목표: " + goal + " (버전 " + version + ")");
        return a;
    }

    public List<Rule> rules(GoalType goal) {
        return rules.getOrDefault(goal, List.of());
    }

    /** minScore 내림차순. */
    public List<Cutoff> cutoffs(GoalType goal) {
        return cutoffs.getOrDefault(goal, List.of());
    }

    public Grade gradeOf(GoalType goal, double score) {
        return gradeOf(cutoffs(goal), score);
    }

    /** 컷오프 목록(minScore 내림차순)으로 등급. 어느 줄에도 못 미치면 E. */
    public static Grade gradeOf(List<Cutoff> sortedDesc, double score) {
        for (Cutoff c : sortedDesc) {
            if (score >= c.minScore()) return c.grade();
        }
        return Grade.E;
    }

    /** 등급 라벨 (grade_cutoffs.label). 없으면 null — 호출부가 기본 문구로 대체. */
    public String label(GoalType goal, Grade grade) {
        if (grade == null) return null;
        for (Cutoff c : cutoffs(goal)) {
            if (c.grade() == grade) return c.label();
        }
        return null;
    }

    /** 재산출 시뮬레이션·적용: 기준값(rules)은 그대로, 앵커·컷오프만 바꾼 스냅샷. */
    public Calibration withRecalibrated(Long recalibrationId, String version, LocalDateTime appliedAt,
                                        Map<GoalType, Anchor> newAnchors, Map<GoalType, List<Cutoff>> newCutoffs) {
        return new Calibration(recalibrationId, version, appliedAt, newAnchors, rules, newCutoffs);
    }

    private static List<Cutoff> sortDesc(List<Cutoff> list) {
        return list.stream()
                .sorted(Comparator.comparingDouble(Cutoff::minScore).reversed())
                .toList();
    }
}
