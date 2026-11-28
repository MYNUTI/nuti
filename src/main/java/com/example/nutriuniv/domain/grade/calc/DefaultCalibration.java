package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.CriterionDirection;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.Nutrient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.example.nutriuniv.domain.grade.entity.CriterionDirection.*;
import static com.example.nutriuniv.domain.grade.entity.Nutrient.*;

/**
 * 처음 기준선 (v3 CORE_001 「점수 식」 11-18 확정 · CORE_007) — 공공 자료 245,988개로 계산한 값.
 * 부팅 때 이 버전({@link #VERSION})이 DB 에 없으면 새 버전으로 시드하고 적용한다(CalibrationService.ensureSeeded).
 * 이후 기준은 관리자 재산출(ADM_003)이 DB 에 새 버전으로 쌓고, 이 클래스는 「DB 가 비어 있을 때의 안전값」으로만 남는다.
 */
public final class DefaultCalibration {

    /** 11-18 점수 식의 처음 기준선. 옛 식(v1)과 다른 버전 이름이어야 부팅 때 새로 시드된다. */
    public static final String VERSION = "s1118";
    public static final String MEMO = "처음 기준선 — CORE_001 점수 식(11-18) · 공공 자료 245,988개 · 아래 끝 1% 분위, 위 끝 식의 최댓값 · 경계 20/40/60/80% 분위";

    private DefaultCalibration() {}

    // ── 아래 끝 · 위 끝 ───────────────────────────────────────────────────────────

    /** Anchor(p1 = 아래 끝(원점수 1% 분위), p99 = 위 끝(식의 최댓값 · 고정)). */
    public static Map<GoalType, Calibration.Anchor> anchors() {
        Map<GoalType, Calibration.Anchor> m = new EnumMap<>(GoalType.class);
        m.put(GoalType.WEIGHT_LOSS, new Calibration.Anchor(-2.258, GradeFormula.maxRaw(GoalType.WEIGHT_LOSS)));
        m.put(GoalType.MUSCLE_GAIN, new Calibration.Anchor(-1.456, GradeFormula.maxRaw(GoalType.MUSCLE_GAIN)));
        return m;
    }

    // ── 기준값 (성분별 · 설명용) ──────────────────────────────────────────────────

    /**
     * grade_criteria 에 남기는 성분별 기준 — 계산은 GradeFormula 가 식 그대로 한다. 여기 값은 기록과 트랜스지방 평가 말(0.2g)에 쓴다.
     * 화면에 기준 수치를 내려 주지 않는다(CORE_004 「성분별 기준선 수치는 내려 주지 않는다」).
     */
    public static Map<GoalType, List<Calibration.Rule>> rules() {
        Map<GoalType, List<Calibration.Rule>> m = new EnumMap<>(GoalType.class);
        for (GoalType goal : GoalType.values()) {
            List<Calibration.Rule> r = new ArrayList<>(8);
            r.add(rule(PROTEIN,       BONUS,   1.0,  11.0,   null, "단백질은 많을수록 가점이에요."));
            r.add(rule(SUGAR,         PENALTY, 1.0,   5.0,   null, "당류는 기준을 넘는 만큼 감점이에요."));
            r.add(rule(SATURATED_FAT, PENALTY, 1.0,   1.5,   15.0, "포화지방은 기준을 넘는 만큼 감점이에요."));
            r.add(rule(SODIUM,        PENALTY, 1.0, 120.0, 2000.0, "나트륨은 기준을 넘는 만큼 감점이에요."));
            r.add(rule(TRANS_FAT,     PENALTY, 1.0,   0.2,    2.0, "트랜스지방은 조금만 넘어도 크게 감점이에요."));
            r.add(rule(CHOLESTEROL,   PENALTY, 0.25, 20.0,  300.0, "콜레스테롤은 기준을 넘는 만큼 조금 감점이에요."));
            switch (goal) {
                case WEIGHT_LOSS -> {
                    r.add(rule(CALORIES,      CALORIE, -1.0, 40.0, null, "다이어트 목표에선 칼로리가 높을수록 감점이에요."));
                    r.add(rule(DIETARY_FIBER, BONUS,    0.1,  3.0,  6.0, "다이어트 목표에선 식이섬유가 많으면 조금 가점이에요."));
                }
                case MUSCLE_GAIN -> r.add(rule(CALORIES, CALORIE, 0.5, 40.0, 400.0,
                        "근육 증가 목표에선 단백질이 충분한 제품만 칼로리가 넉넉할수록 가점이에요."));
            }
            m.put(goal, r);
        }
        return m;
    }

    private static Calibration.Rule rule(Nutrient n, CriterionDirection d, double weight, double threshold, Double max,
                                         String reason) {
        return new Calibration.Rule(n, d, weight, threshold, max, n.unit(), reason);
    }

    // ── 등급 경계 + 구간 이름 ─────────────────────────────────────────────────────

    public static Map<GoalType, List<Calibration.Cutoff>> cutoffs() {
        Map<GoalType, List<Calibration.Cutoff>> m = new EnumMap<>(GoalType.class);
        m.put(GoalType.WEIGHT_LOSS, cutoffs(72.7, 65.8, 53.6, 33.3));
        m.put(GoalType.MUSCLE_GAIN, cutoffs(67.4, 55.3, 45.8, 29.7));
        return m;
    }

    private static List<Calibration.Cutoff> cutoffs(double a, double b, double c, double d) {
        return List.of(
                new Calibration.Cutoff(Grade.A, a, defaultLabel(Grade.A)),
                new Calibration.Cutoff(Grade.B, b, defaultLabel(Grade.B)),
                new Calibration.Cutoff(Grade.C, c, defaultLabel(Grade.C)),
                new Calibration.Cutoff(Grade.D, d, defaultLabel(Grade.D)),
                new Calibration.Cutoff(Grade.E, 0.0, defaultLabel(Grade.E))
        );
    }

    /** 구간 이름 (v3 CORE_003 · RTN_001). DB 경계 행의 label 이 없을 때 쓴다. */
    public static String defaultLabel(Grade grade) {
        if (grade == null) return null;
        return switch (grade) {
            case A -> "매우 높음";
            case B -> "높음";
            case C -> "중간";
            case D -> "낮음";
            case E -> "매우 낮음";
        };
    }

    /** 옛 라벨 → 지금 구간 이름. 부팅 때 grade_cutoffs 의 옛 문구만 바꾼다(재산출이 라벨을 승계하므로). */
    public static final Map<String, String> LEGACY_LABELS = Map.of(
            "아주 잘 맞아요", "매우 높음",
            "잘 맞아요", "높음",
            "보통이에요", "중간",
            "조금 아쉬워요", "낮음",
            "잘 안 맞아요", "매우 낮음",
            "잘 맞음", "매우 높음",
            "맞는 편", "높음",
            "보통", "중간",
            "안 맞는 편", "낮음",
            "안 맞음", "매우 낮음");

    /** DB 시드 전에도 계산이 가능하도록 하는 메모리 스냅샷 (recalibrationId null). */
    public static Calibration snapshot() {
        return new Calibration(null, VERSION, null, anchors(), rules(), cutoffs());
    }

    // ── 설명 문구 시드 (grade_copies) ─────────────────────────────────────────

    public record CopySeed(String code, GoalType goal, String title, String body, int order) {}

    public static final String GUIDE_WEIGHTS_BODY =
            "가점 성분은 일정한 양까지 비례해서, 감점 성분은 기준을 넘는 만큼 반영돼요. 성분별 기준 수치는 공개하지 않아요.";
    public static final String GUIDE_GOAL_LOSS_BODY =
            "100g당 칼로리가 높을수록 감점이에요. 식이섬유가 많으면 조금 가점이에요.";
    public static final String GUIDE_GOAL_GAIN_BODY =
            "단백질이 충분한 제품에서만 칼로리가 넉넉할수록 가점이에요. 당류와 포화지방에서 온 칼로리는 세지 않아요.";
    public static final String GUIDE_STEPS_BODY = "① 식품은 100g당, 음료는 100mL당으로 계산해요. "
            + "② 단백질은 가점, 당류·포화지방·나트륨·트랜스지방·콜레스테롤은 기준을 넘는 만큼 감점이에요. "
            + "③ 칼로리는 목표에 따라 다르게 봐요 — 다이어트는 감점, 근육 증가는 단백질이 충분할 때만 가점이에요. "
            + "④ 합산 점수를 0~100점으로 바꿔요. "
            + "⑤ 점수 구간에 따라 색을 다섯 가지로 나눠요.";
    public static final String FIBER_NOTICE_BODY =
            "식이섬유는 표시 의무 항목이 아니어서 값이 없는 경우가 많아요. 값이 없으면 가점이 없을 뿐 감점하지는 않아요.";

    /** (code, goal 이름 또는 null) → 옛 문구. 이 문구 그대로인 행만 지금 문구로 바꾼다 — 운영자가 고친 문구는 건드리지 않는다. */
    public record LegacyCopy(String code, String goal, String oldBody, String newBody) {}
    public static final List<LegacyCopy> LEGACY_COPY_BODIES = List.of(
            new LegacyCopy("GUIDE_STEPS", null,
                    "① 모든 영양성분을 100g 기준으로 맞춰요. "
                            + "② 단백질·식이섬유는 가점, 당류·포화지방·나트륨·콜레스테롤·트랜스지방은 기준을 넘는 만큼 감점이에요. "
                            + "③ 열량은 목표에 따라 다르게 봐요 — 감량은 감점, 근육 증가는 가점, 일반은 반영하지 않아요. "
                            + "④ 합산 점수를 0~100점으로 정규화해요. "
                            + "⑤ 점수 구간에 따라 A~E 등급을 매겨요.", GUIDE_STEPS_BODY),
            new LegacyCopy("GUIDE_STEPS", null,
                    "① 모든 영양성분을 100g 기준으로 맞춰요. "
                            + "② 단백질·식이섬유는 가점, 당류·포화지방·나트륨·콜레스테롤·트랜스지방은 기준을 넘는 만큼 감점이에요. "
                            + "③ 열량은 목표에 따라 다르게 봐요 — 감량은 감점, 근육 증가는 가점이에요. "
                            + "④ 합산 점수를 0~100점으로 정규화해요. "
                            + "⑤ 점수 구간에 따라 A~E 등급을 매겨요.", GUIDE_STEPS_BODY),
            new LegacyCopy("GUIDE_WEIGHTS", null,
                    "가점 성분은 만점선까지 비례해서 최대 1점씩, 감점 성분은 기준값을 넘는 만큼 최대 1점씩 반영돼요. "
                            + "성분별 기준값은 등급 근거 화면에서 실제 값과 함께 보여드려요.", GUIDE_WEIGHTS_BODY),
            new LegacyCopy("GUIDE_GOAL", "WEIGHT_LOSS",
                    "100g당 열량이 높을수록 감점이에요. 같은 제품이라도 일반 목표보다 등급이 낮아질 수 있어요.", GUIDE_GOAL_LOSS_BODY),
            new LegacyCopy("GUIDE_GOAL", "WEIGHT_LOSS",
                    "100g당 열량이 높을수록 감점이에요. 같은 제품이라도 근육 증가 목표보다 등급이 낮아질 수 있어요.", GUIDE_GOAL_LOSS_BODY),
            new LegacyCopy("GUIDE_GOAL", "MUSCLE_GAIN",
                    "100g당 열량이 높을수록 가점이에요. 단백질이 풍부한 제품이 유리해요.", GUIDE_GOAL_GAIN_BODY),
            new LegacyCopy("FIBER_NOTICE", null,
                    "식이섬유는 표시 의무 항목이 아니어서 값이 없는 경우가 많고, 그럴 땐 0으로 계산하므로 실제보다 낮게 나올 수 있습니다.",
                    FIBER_NOTICE_BODY));

    public static List<CopySeed> copies() {
        List<CopySeed> c = new ArrayList<>();
        c.add(new CopySeed("GUIDE_STEPS", null, "점수는 이렇게 계산해요", GUIDE_STEPS_BODY, 1));
        c.add(new CopySeed("GUIDE_WEIGHTS", null, "성분별 비중", GUIDE_WEIGHTS_BODY, 2));
        c.add(new CopySeed("GUIDE_GOAL", GoalType.WEIGHT_LOSS, "다이어트 목표", GUIDE_GOAL_LOSS_BODY, 3));
        c.add(new CopySeed("GUIDE_GOAL", GoalType.MUSCLE_GAIN, "근육 증가 목표", GUIDE_GOAL_GAIN_BODY, 3));
        c.add(new CopySeed("FIBER_NOTICE", null, "식이섬유 안내", FIBER_NOTICE_BODY, 4));
        c.add(new CopySeed("GRADE_DESC_A", null, "A", "목표에 아주 잘 맞아요. 감점 요인이 거의 없어요.", 1));
        c.add(new CopySeed("GRADE_DESC_B", null, "B", "목표에 잘 맞아요.", 2));
        c.add(new CopySeed("GRADE_DESC_C", null, "C", "보통이에요. 감점 요인이 조금 있어요.", 3));
        c.add(new CopySeed("GRADE_DESC_D", null, "D", "조금 아쉬워요. 감점 요인을 확인해 보세요.", 4));
        c.add(new CopySeed("GRADE_DESC_E", null, "E", "목표에 잘 안 맞아요. 감점 요인이 커요.", 5));
        c.add(new CopySeed("BADGE_PRE_CATEGORY", null, null, "분류 비교 전", 1));
        c.add(new CopySeed("SOURCE_NOTE", null, "출처", "식약처 공공 영양성분DB 기준 · 노출비를 받지 않습니다", 1));
        return c;
    }
}
