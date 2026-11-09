package com.example.nutriuniv.domain.grade.calc;

import com.example.nutriuniv.domain.goal.entity.GoalType;

import java.util.List;

/**
 * 열량구간 4종 (1500/2000/2500/3000 kcal, v3 CORE_008). 두 목표 모두 구간마다 슬롯을 만든다.
 * 요청에 구간이 없거나 4종 밖이면 2000(DEFAULT) — 결과 API 의 kcalBand 파라미터가 {@link #ofOrDefault} 로 읽는다.
 * <p>감량 목표의 에너지밀도 상한 M 은 구간에 따라 400~900 사이를 움직인다(구 PnsCalculator 공식 그대로). 근육 증가는 기준값 표의 max_value(400) 고정.
 */
public enum EerBand {
    B1500(1500), B2000(2000), B2500(2500), B3000(3000);

    /** 1·2차 조회 구간. */
    public static final EerBand DEFAULT = B2000;
    private static final double EER_MIN = 1500.0;
    private static final double EER_MAX = 2600.0;
    private static final double M_MIN   = 400.0;
    private static final double M_MAX   = 900.0;

    private final int kcal;

    EerBand(int kcal) {
        this.kcal = kcal;
    }

    public int kcal() {
        return kcal;
    }

    /** 감량 목표 에너지밀도 상한 M(kcal/100g) = clamp(400 + 500 × (EER − 1500) / 1100, 400, 900). */
    public double energyDensityCap() {
        double m = M_MIN + (M_MAX - M_MIN) * (kcal - EER_MIN) / (EER_MAX - EER_MIN);
        return Math.max(M_MIN, Math.min(M_MAX, m));
    }

    public static EerBand of(int kcal) {
        for (EerBand b : values()) {
            if (b.kcal == kcal) return b;
        }
        throw new IllegalArgumentException("지원하지 않는 열량구간: " + kcal);
    }

    /** 요청 값 → 구간. 없거나 4종 밖이면 2000 (CORE_008). */
    public static EerBand ofOrDefault(Integer kcal) {
        if (kcal == null) return DEFAULT;
        for (EerBand b : values()) {
            if (b.kcal == kcal) return b;
        }
        return DEFAULT;
    }

    /** 슬롯 값으로 에너지밀도 상한. */
    public static double energyDensityCap(int slot) {
        return of(slot).energyDensityCap();
    }

    /** (goal, band) → product_grades.eer_band 슬롯 값. */
    public static int slot(GoalType goal, EerBand band) {
        return band.kcal;
    }

    /** 구간을 모를 때의 조회 슬롯: 2000. */
    public static int defaultSlot(GoalType goal) {
        return slot(goal, DEFAULT);
    }

    /** 목표별 사전계산 슬롯 목록: [1500, 2000, 2500, 3000]. */
    public static List<Integer> slots(GoalType goal) {
        return List.of(B1500.kcal, B2000.kcal, B2500.kcal, B3000.kcal);
    }
}
