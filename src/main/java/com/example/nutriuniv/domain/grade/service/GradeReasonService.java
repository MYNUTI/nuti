package com.example.nutriuniv.domain.grade.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.calc.GradeFormula;
import com.example.nutriuniv.domain.grade.dto.GradeReasonResponse;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 등급 근거 「왜 C인가요?」 (기능명세서 5.2). 현행 기준값으로 100g 기준 기여도를 다시 계산해 감점 요인을 큰 순으로 준다.
 * 영양정보 부족 제품은 진입 불가(409 GRADE_NOT_AVAILABLE). 분류 내 위치는 3차 전까지 주지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GradeReasonService {

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final OwnerResolver ownerResolver;
    private final GradeLookupService gradeLookupService;
    private final CalibrationService calibrationService;
    private final GradeCopyService copyService;

    @Transactional(readOnly = true)
    public GradeReasonResponse reason(Long productId, String goalParam, Actor actor) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductNutrient nutrient = productNutrientRepository.findById(product.getId()).orElse(null);
        if (ProductStatus.of(nutrient) != ProductStatus.ANALYZED) {
            throw new CustomException(ErrorCode.GRADE_NOT_AVAILABLE);
        }

        GoalType goal = resolveGoal(goalParam, actor);
        Calibration cal = calibrationService.current();
        List<Calibration.Rule> rules = cal.rules(goal);
        Map<Nutrient, Calibration.Rule> ruleByNutrient = rules.stream()
                .collect(Collectors.toMap(Calibration.Rule::nutrient, Function.identity(), (a, b) -> a));

        GradeFormula.Input input = GradeLookupService.toInput(nutrient);
        GradeFormula.Raw raw = GradeFormula.raw(goal, EerBand.defaultSlot(goal), input, rules);

        List<GradeReasonResponse.Factor> factors = raw.contributions().stream()
                .filter(c -> c.value() < 0)                                        // 감점 요인만
                .sorted(Comparator.comparingDouble(GradeFormula.Contribution::value)) // 가장 큰 감점부터
                .map(c -> {
                    Calibration.Rule rule = ruleByNutrient.get(c.nutrient());
                    return GradeReasonResponse.Factor.builder()
                            .nutrient(c.nutrient().name())
                            .label(c.nutrient().label())
                            .value(round(input.of(c.nutrient())))
                            .unit(c.nutrient().unit())
                            .threshold(rule == null ? null : round(rule.threshold()))
                            .sentence(rule == null ? null : rule.reasonTemplate())
                            .build();
                })
                .toList();

        String fiberNotice = nutrient.getFiberPer100g() == null ? copyService.body("FIBER_NOTICE") : null;

        return GradeReasonResponse.builder()
                .appliedGoal(goal.name())
                .factors(factors)
                .fiberNotice(fiberNotice)
                .methodology(GradeReasonResponse.Methodology.builder()
                        .steps(splitSteps(copyService.body("GUIDE_STEPS")))
                        .note(copyService.body("GUIDE_GOAL", goal))
                        .build())
                .build();
    }

    private GoalType resolveGoal(String goalParam, Actor actor) {
        if (goalParam != null && !goalParam.isBlank()) {
            return GoalType.from(goalParam);                                          // 허용값 외 400
        }
        return gradeLookupService.resolveGoalType(ownerResolver.resolveOrNull(actor));   // 미설정·동의 전 → GENERAL
    }

    /** 「① … ② …」 한 문단을 단계 목록으로. 원문자(①~⑩)를 구분자로 쓴다. */
    static List<String> splitSteps(String body) {
        if (body == null || body.isBlank()) return List.of();
        return Arrays.stream(body.split("[①-⑩]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 소수 1자리. stripTrailingZeros 는 쓰지 않는다 — 21.0 이 2.1E+1 로 직렬화된다. */
    private static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }
}
