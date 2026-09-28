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
 * 초기 기준 v1 — 구 PnsCalculator v4.1 의 상수를 그대로 옮긴 값. 부팅 시 DB 에 한 번 시드된다(CalibrationService.ensureSeeded).
 * 이 값으로 계산하면 배포 전 product_pns_by_eer 와 같은 점수·등급이 나온다(일반 = 구 health).
 * 이후 기준은 관리자 재산출(4.2)이 DB 에 새 버전으로 쌓고, 이 클래스는 「DB 가 비어 있을 때의 안전값」으로만 남는다.
 */
public final class DefaultCalibration {

    public static final String VERSION = "v1";
    public static final String MEMO = "초기 기준 — 구 PnsCalculator v4.1 상수 이관 (1,924개 채점셋 P1/P99 동결 앵커 · §6 컷오프)";

    private DefaultCalibration() {}

    // ── 앵커 (raw P1/P99) ─────────────────────────────────────────────────────

    public static Map<GoalType, Calibration.Anchor> anchors() {
        Map<GoalType, Calibration.Anchor> m = new EnumMap<>(GoalType.class);
        m.put(GoalType.GENERAL,     new Calibration.Anchor(-1.07, 1.81));   // 구 health
        m.put(GoalType.WEIGHT_LOSS, new Calibration.Anchor(-1.76, 1.35));   // 구 diet
        m.put(GoalType.MUSCLE_GAIN, new Calibration.Anchor(-0.60, 2.69));   // 구 bulk
        return m;
    }

    // ── 기준값 (성분별) ───────────────────────────────────────────────────────

    public static Map<GoalType, List<Calibration.Rule>> rules() {
        Map<GoalType, List<Calibration.Rule>> m = new EnumMap<>(GoalType.class);
        for (GoalType goal : GoalType.values()) {
            List<Calibration.Rule> r = new ArrayList<>(8);
            // 유익 만점선
            r.add(rule(PROTEIN,       BONUS,   1.0, 11.0,  null,  goal, "단백질은 100g당 11g까지 비례해서 가점이에요."));
            r.add(rule(DIETARY_FIBER, BONUS,   1.0,  6.0,  null,  goal, "식이섬유는 100g당 6g까지 비례해서 가점이에요. 표시가 없으면 0으로 계산해요."));
            // 제한 감점 구간 [시작, 최대]
            r.add(rule(SUGAR,         PENALTY, 1.0,   5.0,  100.0, goal, "당류는 100g당 5g을 넘는 만큼 감점이에요."));
            r.add(rule(SATURATED_FAT, PENALTY, 1.0,   1.5,   15.0, goal, "포화지방은 100g당 1.5g을 넘는 만큼 감점이에요."));
            r.add(rule(SODIUM,        PENALTY, 1.0, 120.0, 2000.0, goal, "나트륨은 100g당 120mg을 넘는 만큼 감점이에요."));
            r.add(rule(CHOLESTEROL,   PENALTY, 1.0,  20.0,  300.0, goal, "콜레스테롤은 100g당 20mg을 넘는 만큼 감점이에요."));
            r.add(rule(TRANS_FAT,     PENALTY, 1.0,   0.2,    2.0, goal, "트랜스지방은 100g당 0.2g만 넘어도 크게 감점이에요."));
            // 에너지밀도 — 목표별 부호. 0점 40kcal/100g. 상한 M: 감량은 열량구간별(null), 근육 증가 400 고정, 일반은 미반영(가중치 0)
            switch (goal) {
                case WEIGHT_LOSS -> r.add(rule(CALORIES, CALORIE, -1.0, 40.0, null,  goal, "감량 목표에선 100g당 열량이 높을수록 감점이에요."));
                case MUSCLE_GAIN -> r.add(rule(CALORIES, CALORIE,  1.0, 40.0, 400.0, goal, "근육 증가 목표에선 100g당 열량이 높을수록 가점이에요."));
                case GENERAL     -> r.add(rule(CALORIES, CALORIE,  0.0, 40.0, null,  goal, "일반 목표에선 열량을 점수에 반영하지 않아요."));
            }
            m.put(goal, r);
        }
        return m;
    }

    private static Calibration.Rule rule(Nutrient n, CriterionDirection d, double weight, double threshold, Double max,
                                         GoalType goal, String reason) {
        return new Calibration.Rule(n, d, weight, threshold, max, n.unit(), reason);
    }

    // ── 컷오프 (§6) + 라벨 (5.1 문구 원칙) ──────────────────────────────────────

    public static Map<GoalType, List<Calibration.Cutoff>> cutoffs() {
        Map<GoalType, List<Calibration.Cutoff>> m = new EnumMap<>(GoalType.class);
        m.put(GoalType.GENERAL,     cutoffs(64.0, 55.0, 45.3, 37.1));   // 구 toHealthGrade
        m.put(GoalType.WEIGHT_LOSS, cutoffs(74.3, 65.2, 57.8, 45.5));   // 구 diet
        m.put(GoalType.MUSCLE_GAIN, cutoffs(58.0, 45.4, 36.3, 24.7));   // 구 bulk
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

    /** 등급 라벨 기본값 — 「나쁩니다」 대신 「잘 안 맞아요」. DB 컷오프 행의 label 이 없을 때만 쓴다. */
    public static String defaultLabel(Grade grade) {
        if (grade == null) return null;
        return switch (grade) {
            case A -> "아주 잘 맞아요";
            case B -> "잘 맞아요";
            case C -> "보통이에요";
            case D -> "조금 아쉬워요";
            case E -> "잘 안 맞아요";
        };
    }

    /** DB 시드 전에도 계산이 가능하도록 하는 메모리 스냅샷 (recalibrationId null). */
    public static Calibration snapshot() {
        return new Calibration(null, VERSION, null, anchors(), rules(), cutoffs());
    }

    // ── 설명 문구 시드 (grade_copies) ─────────────────────────────────────────

    public record CopySeed(String code, GoalType goal, String title, String body, int order) {}

    public static List<CopySeed> copies() {
        List<CopySeed> c = new ArrayList<>();
        c.add(new CopySeed("GUIDE_STEPS", null, "등급은 이렇게 계산해요",
                "① 모든 영양성분을 100g 기준으로 맞춰요. "
                        + "② 단백질·식이섬유는 가점, 당류·포화지방·나트륨·콜레스테롤·트랜스지방은 기준을 넘는 만큼 감점이에요. "
                        + "③ 열량은 목표에 따라 다르게 봐요 — 감량은 감점, 근육 증가는 가점, 일반은 반영하지 않아요. "
                        + "④ 합산 점수를 0~100점으로 정규화해요. "
                        + "⑤ 점수 구간에 따라 A~E 등급을 매겨요.", 1));
        c.add(new CopySeed("GUIDE_WEIGHTS", null, "성분별 비중",
                "가점 성분은 만점선까지 비례해서 최대 1점씩, 감점 성분은 기준값을 넘는 만큼 최대 1점씩 반영돼요. "
                        + "성분별 기준값은 등급 근거 화면에서 실제 값과 함께 보여드려요.", 2));
        c.add(new CopySeed("GUIDE_GOAL", GoalType.GENERAL, "일반 목표",
                "열량은 반영하지 않고 영양 성분의 균형만 봐요.", 3));
        c.add(new CopySeed("GUIDE_GOAL", GoalType.WEIGHT_LOSS, "체중 감량 목표",
                "100g당 열량이 높을수록 감점이에요. 같은 제품이라도 일반 목표보다 등급이 낮아질 수 있어요.", 3));
        c.add(new CopySeed("GUIDE_GOAL", GoalType.MUSCLE_GAIN, "근육 증가 목표",
                "100g당 열량이 높을수록 가점이에요. 단백질이 풍부한 제품이 유리해요.", 3));
        c.add(new CopySeed("FIBER_NOTICE", null, "식이섬유 안내",
                "식이섬유는 표시 의무 항목이 아니어서 값이 없는 경우가 많고, 그럴 땐 0으로 계산하므로 실제보다 낮게 나올 수 있습니다.", 4));
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
