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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 등급 산정 기준 안내 (GET /grades/guide). 문구는 grade_copies, 라벨은 현행 grade_cutoffs.
 * 기준선 수치는 내보내지 않는다 (v3 CORE_004).
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
        addSection(sections, "GUIDE_STEPS", null);
        addSection(sections, "GUIDE_WEIGHTS", null);
        addSection(sections, "GUIDE_GOAL", goal);
        addSection(sections, "FIBER_NOTICE", null);

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

    private void addSection(List<GradeGuideResponse.Section> sections, String code, GoalType goal) {
        copyService.find(code, goal).ifPresent(c -> {
            String body = c.getBody() == null ? "" : c.getBody();
            sections.add(GradeGuideResponse.Section.builder().title(c.getTitle()).body(body).build());
        });
    }

    private static List<GradeGuideResponse.GradeScale> defaultScale() {
        List<GradeGuideResponse.GradeScale> list = new ArrayList<>();
        for (Grade g : Grade.values()) {
            list.add(GradeGuideResponse.GradeScale.builder()
                    .grade(g.name()).label(DefaultCalibration.defaultLabel(g)).description(null).build());
        }
        return list;
    }
}
