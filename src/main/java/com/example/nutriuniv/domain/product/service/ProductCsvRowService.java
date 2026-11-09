package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.brand.repository.BrandRepository;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductCategoryChip;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductCategoryChipRepository;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 적재 파일의 제품 하나(같은 master_id 행 묶음)를 독립 트랜잭션으로 넣는다 (v3 DATA_000).
 * 실패해도 다른 제품에 영향이 없고, 그 제품은 이전 적재 값이 그대로 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCsvRowService {

    public enum Outcome { CREATED, UPDATED, UNCHANGED, PROTECTED }

    /**
     * 같은 master_id 행 묶음. 값은 첫 행 기준, 바코드는 모든 행에서 모은다
     * (바코드마다 행을 따로 주는 형식과 한 칸에 여러 개를 주는 형식 둘 다 받는다).
     */
    public record LoadItem(int line, String masterId, Map<String, String> values, List<String> rawBarcodes) {
        String get(String column) {
            String v = values.get(column);
            return v == null ? "" : v.trim();
        }
    }

    private static final String SPLIT = "[|,;\\s]+";

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final ProductCategoryChipRepository chipRepository;
    private final BrandRepository brandRepository;
    private final ProductBarcodeService barcodeService;

    /**
     * @param categoriesByCode 코드 → 분류 (CategorySeeder 가 넣은 목록)
     * @param withChips 파일에 같이 보이는 칩 열이 있는가 — 없으면 기존 칩을 건드리지 않는다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome apply(LoadItem item, Map<String, Category> categoriesByCode, Map<String, Brand> brandCache, boolean withChips) {
        String name = item.get(ProductCsvService.COL_NAME);
        if (name.isEmpty()) throw new CustomException(ErrorCode.BAD_REQUEST, "제품명이 비어 있습니다.");

        Product existing = productRepository.findByMasterId(item.masterId()).orElse(null);
        // 수동 수정 보호 (10.4) — 운영자가 고친 제품은 재적재가 덮어쓰지 않는다
        if (existing != null && existing.isManuallyCorrected()) return Outcome.PROTECTED;
        String hash = item.get(ProductCsvService.COL_HASH);
        if (existing != null && !hash.isEmpty() && hash.equals(existing.getContentHash())) return Outcome.UNCHANGED;

        Category category = primaryCategory(item.get(ProductCsvService.COL_CATEGORY), categoriesByCode);
        Brand brand = brand(item.get(ProductCsvService.COL_BRAND), brandCache);

        Product product = existing != null ? existing : productRepository.save(Product.loaded(item.masterId(), name));
        product.applyLoad(name, category, brand,
                blankToNull(item.get(ProductCsvService.COL_REPORT_NO)),
                blankToNull(item.get(ProductCsvService.COL_SOURCE)),
                blankToNull(item.get(ProductCsvService.COL_MAKER)),
                blankToNull(item.get(ProductCsvService.COL_INGREDIENTS)),
                parseBoolean(item.get(ProductCsvService.COL_DISCONTINUED)),
                blankToNull(hash));

        barcodeService.replace(product, barcodes(item));
        if (withChips) replaceChips(product, category, item.get(ProductCsvService.COL_EXTRA_CHIPS), categoriesByCode);

        ProductNutrient nutrient = productNutrientRepository.findById(product.getId())
                .orElseGet(() -> ProductNutrient.create(product));
        nutrient.update(
                item.get(ProductCsvService.COL_SERVING_SIZE),
                n(item, "calories_kcal"), n(item, "carbohydrate_g"), n(item, "sugar_g"),
                n(item, "protein_g"), n(item, "fat_g"), n(item, "saturated_fat_g"),
                n(item, "trans_fat_g"), n(item, "cholesterol_mg"), n(item, "sodium_mg"),
                n(item, "dietary_fiber_g"),
                n(item, "calories_kcal_per_100g"), n(item, "carbohydrate_g_per_100g"), n(item, "sugar_g_per_100g"),
                n(item, "protein_g_per_100g"), n(item, "fat_g_per_100g"), n(item, "saturated_fat_g_per_100g"),
                n(item, "trans_fat_g_per_100g"), n(item, "cholesterol_mg_per_100g"), n(item, "sodium_mg_per_100g"),
                n(item, "dietary_fiber_g_per_100g"));
        productNutrientRepository.save(nutrient);
        product.updateStatus(ProductStatus.of(nutrient));   // 점수 있음/없음 — 상태 5종(ProductState)의 바탕

        return existing == null ? Outcome.CREATED : Outcome.UPDATED;
    }

    /** 소속 중분류 — 비어 있으면 분류 제외(영유아식·환자식·술·미분류). 있는데 목록에 없으면 이 제품은 실패로 돌려 데이터 쪽이 고치게 한다. */
    private static Category primaryCategory(String raw, Map<String, Category> categoriesByCode) {
        if (raw.isEmpty()) return null;
        Category c = categoriesByCode.get(code(raw));
        if (c == null || c.getDepth() != 2 || c.isAggregate()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "없는 중분류 코드입니다: " + raw);
        }
        return c;
    }

    private Brand brand(String name, Map<String, Brand> cache) {
        if (name.isEmpty()) return null;
        return cache.computeIfAbsent(name, n -> brandRepository.findByName(n).orElseGet(() -> brandRepository.save(Brand.create(n))));
    }

    /** 13자리로 맞춘다(3.1). 형식·체크섬이 틀린 바코드만 빼고 제품은 넣는다. */
    private static List<String> barcodes(LoadItem item) {
        Set<String> out = new LinkedHashSet<>();
        for (String cell : item.rawBarcodes()) {
            for (String raw : cell.trim().split(SPLIT)) {
                if (raw.isEmpty()) continue;
                BarcodeNormalizer.tryNormalize(raw).ifPresentOrElse(out::add,
                        () -> log.warn("[ProductLoad] 바코드 형식/체크섬 오류로 건너뜀 — master_id={} raw={}", item.masterId(), raw));
            }
        }
        return List.copyOf(out);
    }

    /** 같이 보이는 칩 — 최대 2개, 소속 중분류와 같은 코드는 뺀다. 없는 코드나 3개 이상이면 실패. */
    private void replaceChips(Product product, Category primary, String raw, Map<String, Category> categoriesByCode) {
        Set<Long> wanted = new LinkedHashSet<>();
        Map<Long, Category> byId = new HashMap<>();
        for (String token : raw.split(SPLIT)) {
            if (token.isEmpty()) continue;
            Category c = categoriesByCode.get(code(token));
            if (c == null || c.getDepth() != 2) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "없는 칩 코드입니다: " + token);
            }
            if (primary != null && c.getId().equals(primary.getId())) continue;
            wanted.add(c.getId());
            byId.put(c.getId(), c);
        }
        if (wanted.size() > ProductCategoryChip.MAX_PER_PRODUCT) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "같이 보이는 칩은 " + ProductCategoryChip.MAX_PER_PRODUCT + "개까지입니다: " + raw);
        }
        for (ProductCategoryChip chip : chipRepository.findByProductId(product.getId())) {
            if (!wanted.remove(chip.getCategory().getId())) chipRepository.delete(chip);
        }
        for (Long id : wanted) chipRepository.save(ProductCategoryChip.of(product, byId.get(id)));
    }

    /** 엑셀을 거치며 앞 0 이 빠진 코드(505 → 0505)도 받는다. */
    static String code(String raw) {
        String s = raw.trim();
        if (s.length() == 3 && s.chars().allMatch(Character::isDigit)) return "0" + s;
        return s;
    }

    static boolean parseBoolean(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "", "false", "0", "n", "no", "f" -> false;
            case "true", "1", "y", "yes", "t" -> true;
            default -> throw new CustomException(ErrorCode.BAD_REQUEST, "is_discontinued 값을 읽을 수 없습니다: " + raw);
        };
    }

    private static BigDecimal n(LoadItem item, String column) {
        return parseNutrient(item.get(column));
    }

    /** 빈 칸·숫자 아님은 결측(null) — 0 으로 채우면 「점수 없음」 판정(DATA_003)이 불가능해진다. */
    static BigDecimal parseNutrient(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String numOnly = raw.replaceAll("[^0-9.]", "");
        if (numOnly.isEmpty()) return null;
        try {
            return new BigDecimal(numOnly);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
