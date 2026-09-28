package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.DefaultCalibration;
import com.example.nutriuniv.domain.grade.dto.GradeGuideResponse;
import com.example.nutriuniv.domain.grade.entity.Grade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * 등급 산정 기준 안내 (기능명세서 4.1, GET /grades/guide). 문구는 grade_copies, 기준값은 현행 grade_criteria 에서 읽어
 * 재산출 뒤에도 설명과 실제 산식이 어긋나지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class GradeGuideService {

    private final CalibrationService calibrationService;
    private final GradeCopyService copyService;
    private final GradeLookupService gradeLookupService;
    private final OwnerResolver ownerResolver;

    @Transactional(readOnly = true)
    public GradeGuideResponse guide(String goalParam, Actor actor) {
        GoalType goal = goalParam != null && !goalParam.isBlank()
                ? GoalType.from(goalParam)
                : gradeLookupService.resolveGoalType(ownerResolver.resolveOrNull(actor));
        Calibration cal = calibrationService.current();

        List<GradeGuideResponse.Section> sections = new ArrayList<>();
        addSection(sections, "GUIDE_STEPS", null, null);
        addSection(sections, "GUIDE_WEIGHTS", null, thresholdSummary(cal.rules(goal)));
        addSection(sections, "GUIDE_GOAL", goal, null);
        addSection(sections, "FIBER_NOTICE", null, null);

        List<GradeGuideResponse.GradeScale> scale = cal.cutoffs(goal).stream()
                .sorted(Comparator.comparing(Calibration.Cutoff::grade))
                .map(c -> GradeGuideResponse.GradeScale.builder()
                        .grade(c.grade().name())
                        .label(c.label() != null ? c.label() : DefaultCalibration.defaultLabel(c.grade()))
                        .description(copyService.body("GRADE_DESC_" + c.grade().name()))
                        .build())
                .toList();
        if (scale.isEmpty()) {
            scale = defaultScale();
        }

        return GradeGuideResponse.builder()
                .version(cal.version())
                .updatedAt(cal.appliedAt())
                .appliedGoal(goal.name())
                .sections(sections)
                .gradeScale(scale)
                .build();
    }

    private void addSection(List<GradeGuideResponse.Section> sections, String code, GoalType goal, String appendix) {
        copyService.find(code, goal).ifPresent(c -> {
            String body = c.getBody() == null ? "" : c.getBody();
            if (appendix != null && !appendix.isBlank()) {
                body = body.isBlank() ? appendix : body + "\n" + appendix;
            }
            sections.add(GradeGuideResponse.Section.builder().title(c.getTitle()).body(body).build());
        });
    }

    /** 현행 기준값 요약 — 「가점 만점선: 단백질 11g · 식이섬유 6g / 감점 시작: 당류 5g · … / 열량: …」 (100g 기준). */
    static String thresholdSummary(List<Calibration.Rule> rules) {
        StringJoiner bonus = new StringJoiner(" · ");
        StringJoiner penalty = new StringJoiner(" · ");
        String calorie = null;
        for (Calibration.Rule r : rules) {
            String value = fmt(r.threshold()) + r.unit();
            switch (r.direction()) {
                case BONUS -> bonus.add(r.nutrient().label() + " " + value);
                case PENALTY -> penalty.add(r.nutrient().label() + " " + value + "부터");
                case CALORIE -> calorie = r.weight() == 0
                        ? "열량은 반영하지 않아요."
                        : "열량은 100g당 " + value + "를 넘는 만큼 " + (r.weight() < 0 ? "감점" : "가점") + "이에요.";
            }
        }
        List<String> parts = new ArrayList<>();
        if (bonus.length() > 0)   parts.add("가점 만점선(100g당): " + bonus);
        if (penalty.length() > 0) parts.add("감점 시작(100g당): " + penalty);
        if (calorie != null)      parts.add(calorie);
        return String.join(" / ", parts);
    }

    private static List<GradeGuideResponse.GradeScale> defaultScale() {
        List<GradeGuideResponse.GradeScale> list = new ArrayList<>();
        for (Grade g : Grade.values()) {
            list.add(GradeGuideResponse.GradeScale.builder()
                    .grade(g.name()).label(DefaultCalibration.defaultLabel(g)).description(null).build());
        }
        return list;
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
