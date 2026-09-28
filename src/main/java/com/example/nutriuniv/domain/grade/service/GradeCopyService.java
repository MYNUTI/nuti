package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.GradeCopy;
import com.example.nutriuniv.domain.grade.repository.GradeCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 등급 설명·안내 문구(grade_copies) 조회. 목표별 행이 있으면 그것을, 없으면 목표 공통(goal null) 행을 준다.
 * 관리자가 DB 에서 바로 고칠 수 있어야 하므로 캐시하지 않는다(작은 표).
 */
@Service
@RequiredArgsConstructor
public class GradeCopyService {

    private final GradeCopyRepository copyRepository;

    @Transactional(readOnly = true)
    public Optional<GradeCopy> find(String code, GoalType goal) {
        List<GradeCopy> rows = copyRepository.findByCopyCodeOrderByDisplayOrderAsc(code);
        if (goal != null) {
            Optional<GradeCopy> forGoal = rows.stream().filter(c -> c.getGoal() == goal).findFirst();
            if (forGoal.isPresent()) return forGoal;
        }
        return rows.stream().filter(c -> c.getGoal() == null).findFirst();
    }

    public String body(String code, GoalType goal) {
        return find(code, goal).map(GradeCopy::getBody).orElse(null);
    }

    public String body(String code) {
        return body(code, null);
    }

    public String title(String code, GoalType goal) {
        return find(code, goal).map(GradeCopy::getTitle).orElse(null);
    }
}
