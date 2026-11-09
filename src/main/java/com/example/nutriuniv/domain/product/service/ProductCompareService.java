package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Fact;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.grade.service.CalibrationService;
import com.example.nutriuniv.domain.product.dto.AlternativesResponse;
import com.example.nutriuniv.domain.product.dto.CompareResponse;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.dto.ProductResultResponse;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.util.ServingSize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 더 맞는 제품 (v3 COMP_001) · 비교 (COMP_002).
 * <ul>
 *   <li>더 맞는 제품: 같은 중분류 · 순위 대상(분석 완료 · 생산 중) · 요청 목표 점수가 더 높은 제품, 점수 높은 순</li>
 *   <li>차이 2개: 100g(음료 100mL)당, 목표 기준으로 나아진 성분 중 1일 기준치 % 차이가 큰 두 개(트랜스지방은 기준치가 없어 g 그대로 비교에서 뺀다)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProductCompareService {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int MIN_COMPARE = 2;
    public static final int MAX_COMPARE = 4;
    public static final int DIFF_COUNT = 2;

    private final ProductCardService cardService;
    private final ProductListQuery listQuery;
    private final ProductNutrientRepository productNutrientRepository;
    private final CalibrationService calibrationService;
    private final OwnerResolver ownerResolver;

    // ── GET /products/{id}/alternatives ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public AlternativesResponse alternatives(Long productId, String goalParam, Integer kcalBand, int page, int size, Actor actor) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        GoalType goal = goal(goalParam);
        EerBand band = EerBand.ofOrDefault(kcalBand);
        Owner owner = ownerResolver.resolveOrNull(actor);

        ProductCardService.Row row = cardService.rows(List.of(productId)).get(productId);
        if (row == null) throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        ProductCard current = cardService.withBetter(cardService.cards(List.of(productId), goal, band, owner, null), goal, band).get(0);

        ProductCard.GoalGrade g = current.grade(goal.name());
        if (g == null || row.categoryId() == null) {           // 점수 · 중분류가 없으면 비교할 수 없다
            return new AlternativesResponse(current, goal.name(), band.kcal(), 0, page, size, false, List.of());
        }

        ProductListQuery.Page hits = listQuery.better(row.categoryId(), goal, EerBand.slot(goal, band), g.score(),
                productId, size, page * size);
        List<ProductCard> cards = cardService.cards(hits.ids(), goal, band, owner, null);
        Map<Long, ProductCardService.Row> rows = cardService.rows(hits.ids());
        List<AlternativesResponse.Alternative> items = new ArrayList<>(cards.size());
        for (ProductCard c : cards) {
            ProductCardService.Row r = rows.get(c.productId());
            items.add(new AlternativesResponse.Alternative(c, r == null ? List.of() : diffs(goal, row.per100g(), r.per100g())));
        }
        return new AlternativesResponse(current, goal.name(), band.kcal(), hits.total(), page, size,
                (long) (page + 1) * size < hits.total(), items);
    }

    /** 후보 − 지금, 목표 기준 나아진 성분 중 1일 기준치 % 차이가 큰 순 2개. */
    static List<AlternativesResponse.Diff> diffs(GoalType goal, Map<Nutrient, BigDecimal> base, Map<Nutrient, BigDecimal> cand) {
        record Gap(Fact fact, BigDecimal diff, double pct) {}
        List<Gap> gaps = new ArrayList<>();
        for (Nutrient n : Nutrient.values()) {
            Fact f = Fact.of(n);
            BigDecimal a = base.get(n), b = cand.get(n);
            if (a == null || b == null || f.dailyValue() == null) continue;
            BigDecimal d = b.subtract(a);
            if (d.signum() == 0 || (d.signum() > 0) != moreIsBetter(goal, n)) continue;
            gaps.add(new Gap(f, d, Math.abs(d.doubleValue()) * 100.0 / f.dailyValue()));
        }
        return gaps.stream()
                .sorted(Comparator.comparingDouble(Gap::pct).reversed().thenComparing(x -> x.fact().ordinal()))
                .limit(DIFF_COUNT)
                .map(x -> new AlternativesResponse.Diff(x.fact().graded().name(), x.fact().label(), x.fact().unit(), x.diff(),
                        x.fact().label() + " " + (x.diff().signum() < 0 ? "−" : "+")
                                + ProductCardService.plain(x.diff().abs()) + x.fact().unit()))
                .toList();
    }

    private static boolean moreIsBetter(GoalType goal, Nutrient n) {
        return switch (n) {
            case PROTEIN, DIETARY_FIBER -> true;
            case CALORIES -> goal == GoalType.MUSCLE_GAIN;
            default -> false;
        };
    }

    // ── GET /products/compare ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CompareResponse compare(List<Long> idsParam, String goalParam, Integer kcalBand, Actor actor) {
        List<Long> ids = idsParam == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(idsParam));
        if (ids.size() < MIN_COMPARE || ids.size() > MAX_COMPARE) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "ids는 서로 다른 제품 " + MIN_COMPARE + "~" + MAX_COMPARE + "개여야 합니다.");
        }
        GoalType goal = goal(goalParam);
        EerBand band = EerBand.ofOrDefault(kcalBand);
        Owner owner = ownerResolver.resolveOrNull(actor);

        List<ProductCard> cards = cardService.cards(ids, goal, band, owner, null);
        if (cards.size() != ids.size()) throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        Map<Long, ProductNutrient> nutrients = new HashMap<>();
        productNutrientRepository.findAllById(ids).forEach(n -> nutrients.put(n.getId(), n));
        double trans = ProductResultService.threshold(calibrationService.current(), Nutrient.TRANS_FAT);

        List<CompareResponse.Item> items = new ArrayList<>(cards.size());
        for (ProductCard c : cards) {
            ProductNutrient n = nutrients.get(c.productId());
            ServingSize serving = n == null ? null : ServingSize.parse(n.getServingSize());
            items.add(new CompareResponse.Item(c,
                    serving == null ? null : new ProductResultResponse.Serving(serving.text(), serving.amount(), serving.unit()),
                    ServingSize.basisOf(serving),
                    n == null ? List.of() : ProductResultService.nutrientRows(n, ProductResultService.per100g(n), trans, Map.of())));
        }
        return new CompareResponse(goal.name(), band.kcal(), items);
    }

    public static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "ids는 쉼표로 구분한 제품 ID 입니다.");
        }
    }

    private static GoalType goal(String raw) {
        return raw == null || raw.isBlank() ? GoalType.DEFAULT : GoalType.from(raw);
    }
}
