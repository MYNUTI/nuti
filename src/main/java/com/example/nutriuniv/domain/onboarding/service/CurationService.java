package com.example.nutriuniv.domain.onboarding.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.grade.entity.ProductGrade;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.onboarding.dto.CurationSamplesResponse;
import com.example.nutriuniv.domain.onboarding.entity.CurationSample;
import com.example.nutriuniv.domain.onboarding.repository.CurationSampleRepository;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 샘플 큐레이션 관리 (기능명세서 10.1).
 * <ul>
 *   <li>조회: 각 제품의 현재 등급(일반 기준)을 다시 계산해 보여준다 — 재산출로 바뀌면 관리자가 바로 안다</li>
 *   <li>A등급 20건 미만, A 또는 D 없음, 픽커 대체 발동 → 경고. 지정 제품이 비활성화됐으면 자동 제외 + 경고</li>
 *   <li>지정: 분석 완료 제품만. 목록으로 교체(순서 = 노출 순서)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CurationService {

    public static final int MIN_GRADE_A = 20;

    private final CurationSampleRepository curationRepository;
    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final ProductGradeRepository productGradeRepository;
    private final GradeLookupService gradeLookupService;
    private final CurationWarningStore warningStore;

    @Transactional
    public CurationSamplesResponse list() {
        List<CurationSample> rows = curationRepository.findAllByOrderByIsActiveDescDisplayOrderAscIdAsc();
        List<Long> ids = rows.stream().map(CurationSample::getProductId).toList();
        Map<Long, Product> products = ids.isEmpty() ? Map.of()
                : productRepository.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductNutrient> nutrients = ids.isEmpty() ? Map.of()
                : productNutrientRepository.findAllById(ids).stream().collect(Collectors.toMap(ProductNutrient::getId, Function.identity()));
        int slot = EerBand.defaultSlot(GoalType.GENERAL);
        Map<Long, ProductGrade> grades = ids.isEmpty() ? Map.of()
                : productGradeRepository.findByProductIdInAndGoalAndEerBand(ids, GoalType.GENERAL, slot).stream()
                .collect(Collectors.toMap(ProductGrade::getProductId, Function.identity()));

        List<String> warnings = new ArrayList<>();
        List<CurationSamplesResponse.Item> items = new ArrayList<>();
        long active = 0, aCount = 0, dCount = 0;

        for (CurationSample cs : rows) {
            Product p = products.get(cs.getProductId());
            if (cs.isActive() && (p == null || !p.isActive())) {
                cs.deactivate();                                             // 비활성 제품 자동 제외 + 알림
                warnings.add("비활성 제품을 큐레이션에서 자동 제외했습니다: " + (p == null ? "id=" + cs.getProductId() : p.getName()));
            }
            String grade = currentGrade(cs.getProductId(), nutrients.get(cs.getProductId()), grades.get(cs.getProductId()));
            if (cs.isActive()) {
                active++;
                if (Grade.A.name().equals(grade)) aCount++;
                if (Grade.D.name().equals(grade)) dCount++;
            }
            items.add(CurationSamplesResponse.Item.builder()
                    .productId(cs.getProductId())
                    .name(p == null ? null : p.getName())
                    .grade(grade)
                    .displayOrder(cs.getDisplayOrder())
                    .active(cs.isActive())
                    .build());
        }

        if (aCount < MIN_GRADE_A) warnings.add(String.format("A등급 %d건 — 최소 %d건을 유지해야 합니다(재산출 전에는 자동으로 채워지지 않음).", aCount, MIN_GRADE_A));
        if (aCount == 0) warnings.add("A등급 제품이 없습니다 — 픽커가 대체 등급으로 동작합니다.");
        if (dCount == 0) warnings.add("D등급 제품이 없습니다 — 픽커가 대체 등급으로 동작합니다.");
        warnings.addAll(warningStore.recent());

        return CurationSamplesResponse.builder()
                .items(items).activeCount(active).gradeACount(aCount).warnings(warnings).build();
    }

    @Transactional
    public void replace(List<Long> productIds, Long adminUserId) {
        if (productIds == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "productIds는 필수입니다(빈 배열이면 전체 해제).");
        }
        List<Long> distinct = productIds.stream().filter(Objects::nonNull).distinct().toList();

        if (!distinct.isEmpty()) {
            Map<Long, Product> products = productRepository.findAllById(distinct).stream()
                    .filter(Product::isActive).collect(Collectors.toMap(Product::getId, Function.identity()));
            List<Long> missing = distinct.stream().filter(id -> !products.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND, "없거나 비활성인 제품: " + missing);
            }
            Map<Long, ProductNutrient> nutrients = productNutrientRepository.findAllById(distinct).stream()
                    .collect(Collectors.toMap(ProductNutrient::getId, Function.identity()));
            List<Long> insufficient = distinct.stream()
                    .filter(id -> ProductStatus.of(nutrients.get(id)) != ProductStatus.ANALYZED).toList();
            if (!insufficient.isEmpty()) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "분석 완료 제품만 지정할 수 있습니다. 영양정보 부족: " + insufficient);
            }
        }

        Map<Long, CurationSample> existing = curationRepository.findAll().stream()
                .collect(Collectors.toMap(CurationSample::getProductId, Function.identity()));
        Set<Long> keep = new HashSet<>(distinct);
        for (int i = 0; i < distinct.size(); i++) {
            Long id = distinct.get(i);
            CurationSample cs = existing.get(id);
            if (cs == null) {
                curationRepository.save(CurationSample.create(id, i, adminUserId));
            } else {
                cs.activate(i, adminUserId);
            }
        }
        existing.values().stream().filter(cs -> !keep.contains(cs.getProductId()) && cs.isActive()).forEach(CurationSample::deactivate);
    }

    /** 조회 시 재계산: 사전계산 행 → 없으면 영양정보로 즉석 계산 → 영양정보 부족이면 null. */
    private String currentGrade(Long productId, ProductNutrient nutrient, ProductGrade row) {
        if (row != null) return row.getGrade().name();
        if (ProductStatus.of(nutrient) != ProductStatus.ANALYZED) return null;
        return gradeLookupService.computeOnTheFly(nutrient, GoalType.GENERAL).grade();
    }
}
