package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.service.GradeBatchService;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.product.dto.AdminProductChangeLogResponse;
import com.example.nutriuniv.domain.product.dto.AdminProductNutrientUpdateRequest;
import com.example.nutriuniv.domain.product.dto.AdminProductNutrientUpdateResponse;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductChangeLog;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductChangeLogRepository;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 영양성분 수정 (기능명세서 10.4 「영양정보를 고치면 등급도 바뀐다」·「수정한 값이 다음 적재 때 덮어쓰이지 않게」).
 * 전달된 필드만 바꾸고 → 분석 완료 판정 갱신 → 그 제품의 9슬롯 등급 즉시 재계산 → 재적재 보호 ON → 수정 이력 한 줄.
 * 제보 처리(PATCH /admin/reports)와 별개로도 쓸 수 있다 — 1차는 엑셀 재업로드 외에 영양성분을 고칠 길이 없었다.
 */
@Service
@RequiredArgsConstructor
public class ProductNutrientAdminService {

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final ProductChangeLogRepository changeLogRepository;
    private final GradeBatchService gradeBatchService;

    @Transactional
    public AdminProductNutrientUpdateResponse update(Long productId, AdminProductNutrientUpdateRequest req, Long adminUserId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductNutrient n = productNutrientRepository.findById(productId).orElseGet(() -> ProductNutrient.create(product));

        List<String> changes = new ArrayList<>();
        String servingSize = pick("servingSize", req.getServingSize(), n.getServingSize(), changes);
        BigDecimal calories       = pick("calories",       req.getCalories(),       n.getCalories(),       changes);
        BigDecimal carbohydrate   = pick("carbohydrate",   req.getCarbohydrate(),   n.getCarbohydrate(),   changes);
        BigDecimal sugar          = pick("sugar",          req.getSugar(),          n.getSugar(),          changes);
        BigDecimal protein        = pick("protein",        req.getProtein(),        n.getProtein(),        changes);
        BigDecimal fat            = pick("fat",            req.getFat(),            n.getFat(),            changes);
        BigDecimal saturatedFat   = pick("saturatedFat",   req.getSaturatedFat(),   n.getSaturatedFat(),   changes);
        BigDecimal transFat       = pick("transFat",       req.getTransFat(),       n.getTransFat(),       changes);
        BigDecimal cholesterol    = pick("cholesterol",    req.getCholesterol(),    n.getCholesterol(),    changes);
        BigDecimal sodium         = pick("sodium",         req.getSodium(),         n.getSodium(),         changes);
        BigDecimal fiber          = pick("fiber",          req.getFiber(),          n.getFiber(),          changes);
        BigDecimal caloriesPer100g      = pick("caloriesPer100g",      req.getCaloriesPer100g(),      n.getCaloriesPer100g(),      changes);
        BigDecimal carbohydratePer100g  = pick("carbohydratePer100g",  req.getCarbohydratePer100g(),  n.getCarbohydratePer100g(),  changes);
        BigDecimal sugarPer100g         = pick("sugarPer100g",         req.getSugarPer100g(),         n.getSugarPer100g(),         changes);
        BigDecimal proteinPer100g       = pick("proteinPer100g",       req.getProteinPer100g(),       n.getProteinPer100g(),       changes);
        BigDecimal fatPer100g           = pick("fatPer100g",           req.getFatPer100g(),           n.getFatPer100g(),           changes);
        BigDecimal saturatedFatPer100g  = pick("saturatedFatPer100g",  req.getSaturatedFatPer100g(),  n.getSaturatedFatPer100g(),  changes);
        BigDecimal transFatPer100g      = pick("transFatPer100g",      req.getTransFatPer100g(),      n.getTransFatPer100g(),      changes);
        BigDecimal cholesterolPer100g   = pick("cholesterolPer100g",   req.getCholesterolPer100g(),   n.getCholesterolPer100g(),   changes);
        BigDecimal sodiumPer100g        = pick("sodiumPer100g",        req.getSodiumPer100g(),        n.getSodiumPer100g(),        changes);
        BigDecimal fiberPer100g         = pick("fiberPer100g",         req.getFiberPer100g(),         n.getFiberPer100g(),         changes);

        if (changes.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "수정할 필드가 없습니다.");
        }
        for (BigDecimal v : new BigDecimal[]{calories, carbohydrate, sugar, protein, fat, saturatedFat, transFat, cholesterol, sodium, fiber,
                caloriesPer100g, carbohydratePer100g, sugarPer100g, proteinPer100g, fatPer100g, saturatedFatPer100g, transFatPer100g,
                cholesterolPer100g, sodiumPer100g, fiberPer100g}) {
            if (v != null && v.signum() < 0) throw new CustomException(ErrorCode.BAD_REQUEST, "영양성분 값은 0 이상이어야 합니다.");
        }

        n.update(servingSize, calories, carbohydrate, sugar, protein, fat, saturatedFat, transFat, cholesterol, sodium, fiber,
                caloriesPer100g, carbohydratePer100g, sugarPer100g, proteinPer100g, fatPer100g, saturatedFatPer100g, transFatPer100g,
                cholesterolPer100g, sodiumPer100g, fiberPer100g);
        productNutrientRepository.save(n);
        productNutrientRepository.flush();                                  // JDBC 등급 재계산 전에 영양성분을 확정

        ProductStatus status = ProductStatus.of(n);
        product.updateStatus(status);
        product.markManuallyCorrected();

        // 등급 즉시 재계산 (9슬롯). 부족이면 슬롯 삭제만
        Map<GoalType, String> grades = gradeBatchService.recomputeOne(productId,
                status == ProductStatus.ANALYZED ? GradeLookupService.toInput(n) : null);

        String summary = String.join(" / ", changes) + " → " + status.label()
                + (req.getMemo() == null || req.getMemo().isBlank() ? "" : " — " + req.getMemo().trim());
        changeLogRepository.save(ProductChangeLog.create(productId, req.getReportId(), adminUserId, ProductChangeLog.Source.ADMIN_NUTRIENT_UPDATE, summary));

        Map<String, String> gradeOut = new LinkedHashMap<>();
        grades.forEach((g, v) -> gradeOut.put(g.name(), v));
        return AdminProductNutrientUpdateResponse.builder()
                .productId(productId)
                .status(status.name())
                .changedFields(changes)
                .grades(gradeOut)
                .manuallyCorrected(true)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminProductChangeLogResponse changeLogs(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        List<AdminProductChangeLogResponse.Item> items = changeLogRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(l -> AdminProductChangeLogResponse.Item.builder()
                        .id(l.getId())
                        .source(l.getSource().name())
                        .reportId(l.getReportId())
                        .adminUserId(l.getAdminUserId())
                        .summary(l.getSummary())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();
        return AdminProductChangeLogResponse.builder()
                .productId(productId)
                .manuallyCorrected(product.isManuallyCorrected())
                .correctedAt(product.getCorrectedAt())
                .items(items)
                .build();
    }

    /** 요청값이 있으면 그것(바뀌면 이력에 「필드: 전 → 후」), 없으면 기존 값. */
    private static <T> T pick(String field, T requested, T current, List<String> changes) {
        if (requested == null) return current;
        boolean same = requested instanceof BigDecimal rb && current instanceof BigDecimal cb ? rb.compareTo(cb) == 0 : requested.equals(current);
        if (!same) changes.add(field + ": " + current + " → " + requested);
        return requested;
    }
}
