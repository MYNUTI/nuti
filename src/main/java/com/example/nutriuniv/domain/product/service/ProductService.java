package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.brand.repository.BrandRepository;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.repository.CoupangLinkRepository;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.recommendation.repository.ProductVectorByDietRepository;
import com.example.nutriuniv.domain.recommendation.repository.RecommendationCacheRepository;
import com.example.nutriuniv.domain.recommendation.service.RecommendationService;
import com.example.nutriuniv.domain.user.entity.UserNutrition;
import com.example.nutriuniv.domain.user.repository.UserNutritionRepository;
import com.example.nutriuniv.domain.product.dto.*;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.enums.NutrientClaim;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.specification.NutrientClaimSpecification;
import com.example.nutriuniv.domain.product.specification.ProductSpecification;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ALLOWED_SORT_VALUES = Set.of("POPULAR", "SCORE", "ACCURACY", "RECOMMENDED");
    private static final Set<String> ALLOWED_LINK_STATUS = Set.of("LINKED", "UNLINKED", "FAILED");

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final SavedProductRepository savedProductRepository;
    private final EntityManager entityManager;
    private final CoupangLinkRepository coupangLinkRepository;
    private final GradeLookupService gradeLookupService;
    private final UserNutritionRepository userNutritionRepository;
    private final RecommendationCacheRepository recommendationCacheRepository;
    private final ProductVectorByDietRepository productVectorByDietRepository;
    private final RecommendationService recommendationService;

    // ── 일반 유저: 상품 목록 조회 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProductPageResponse getProducts(ProductSearchRequest request, Long userId) {

        if (request.getPage() < 0 || request.getSize() <= 0) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM);
        }
        if (request.getSort() != null && !ALLOWED_SORT_VALUES.contains(request.getSort())) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM);
        }
        validateMinMax(request);

        Specification<Product> spec = Specification.where(ProductSpecification.isActive());
        spec = andIf(spec, request.getKeyword() != null, ProductSpecification.hasKeyword(request.getKeyword()));
        spec = andIf(spec, request.getCategoryIds() != null && !request.getCategoryIds().isEmpty(), ProductSpecification.hasCategory(request.getCategoryIds()));
        spec = andIf(spec, request.getBrandIds() != null && !request.getBrandIds().isEmpty(), ProductSpecification.hasBrand(request.getBrandIds()));
        spec = andIf(spec, request.getMinCalories() != null, ProductSpecification.hasMinCalories(request.getMinCalories()));
        spec = andIf(spec, request.getMaxCalories() != null, ProductSpecification.hasMaxCalories(request.getMaxCalories()));
        spec = andIf(spec, request.getMinProtein() != null, ProductSpecification.hasMinProtein(request.getMinProtein()));
        spec = andIf(spec, request.getMaxProtein() != null, ProductSpecification.hasMaxProtein(request.getMaxProtein()));
        spec = andIf(spec, request.getMinFat() != null, ProductSpecification.hasMinFat(request.getMinFat()));
        spec = andIf(spec, request.getMaxFat() != null, ProductSpecification.hasMaxFat(request.getMaxFat()));
        spec = andIf(spec, request.getMinCarbohydrate() != null, ProductSpecification.hasMinCarbohydrate(request.getMinCarbohydrate()));
        spec = andIf(spec, request.getMaxCarbohydrate() != null, ProductSpecification.hasMaxCarbohydrate(request.getMaxCarbohydrate()));
        spec = andIf(spec, request.getMinSugar() != null, ProductSpecification.hasMinSugar(request.getMinSugar()));
        spec = andIf(spec, request.getMaxSugar() != null, ProductSpecification.hasMaxSugar(request.getMaxSugar()));
        spec = andIf(spec, request.getMinSodium() != null, ProductSpecification.hasMinSodium(request.getMinSodium()));
        spec = andIf(spec, request.getMaxSodium() != null, ProductSpecification.hasMaxSodium(request.getMaxSodium()));
        spec = andIf(spec, request.getMinNutritionScore() != null, ProductSpecification.hasMinNutritionScore(request.getMinNutritionScore()));
        spec = andIf(spec, request.getMaxNutritionScore() != null, ProductSpecification.hasMaxNutritionScore(request.getMaxNutritionScore()));

        List<NutrientClaim> claims = parseClaims(request.getNutrientClaims());
        if (!claims.isEmpty()) {
            spec = spec.and(NutrientClaimSpecification.hasClaims(claims));
        }

        // 목표 결정 — 등급은 목표별 사전계산, 열량구간은 1·2차 2000 고정(슬롯은 GradeLookupService 가 정한다)
        GoalType goal = gradeLookupService.resolveGoalType(userId);

        // SCORE/ACCURACY(키워드 있을 때)/RECOMMENDED(로그인 시) 정렬은 네이티브 쿼리, 나머지는 JPA 정렬
        boolean hasKeyword = request.getKeyword() != null && !request.getKeyword().isBlank();
        boolean hasFilter = (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty())
                || (request.getBrandIds() != null && !request.getBrandIds().isEmpty())
                || !claims.isEmpty();

        Page<Product> page;
        if ("SCORE".equals(request.getSort())) {
            // 카테고리/브랜드/nutrientClaims 필터가 있으면 JPA spec으로 ID 목록 추출
            // (카테고리는 하위 카테고리까지 포함하는 서브쿼리 로직이 있어서 JPA spec으로 처리해야 함)
            List<Long> claimFilteredIds = null;
            if (hasFilter) {
                claimFilteredIds = productRepository.findAll(spec, PageRequest.of(0, Integer.MAX_VALUE))
                        .stream().map(Product::getId).collect(Collectors.toList());
            }
            page = findAllOrderByGradeScore(request, goal, claimFilteredIds);
        } else if ("ACCURACY".equals(request.getSort()) && hasKeyword) {
            // 정확도순: 키워드가 있을 때만 트라이그램 유사도 정렬. 키워드 없으면 아래 else로 폴백(최신순).
            List<Long> claimFilteredIds = null;
            if (hasFilter) {
                claimFilteredIds = productRepository.findAll(spec, PageRequest.of(0, Integer.MAX_VALUE))
                        .stream().map(Product::getId).collect(Collectors.toList());
            }
            page = findAllOrderByRelevance(request, claimFilteredIds);
        } else if ("RECOMMENDED".equals(request.getSort()) && userId != null) {
            // 추천순: 로그인 유저만 개인화. 새 테이블 없이 recommendation_cache(CF)/product_vectors_by_diet(CONTENT) 재사용,
            // 둘 다 신호 없으면 인기순으로 자연 폴백(findAllOrderByPreference 내부에서 처리).
            List<Long> claimFilteredIds = null;
            if (hasFilter) {
                claimFilteredIds = productRepository.findAll(spec, PageRequest.of(0, Integer.MAX_VALUE))
                        .stream().map(Product::getId).collect(Collectors.toList());
            }
            page = findAllOrderByPreference(request, userId, claimFilteredIds);
        } else {
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), resolveSort(request.getSort()));
            page = productRepository.findAll(spec, pageable);
        }
        List<Long> productIds = page.getContent().stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, String> gradeMap = gradeLookupService.lookupGrades(productIds, goal);

        Map<Long, Integer> priceMap = coupangLinkRepository.findByProductIdIn(productIds).stream()
                .filter(l -> "LINKED".equals(l.getLinkStatus()))
                .collect(Collectors.toMap(l -> l.getProduct().getId(), CoupangLink::getProductPrice));

        List<ProductListResponse> items = page.getContent().stream()
                .map(p -> toListResponse(p, userId, gradeMap.get(p.getId()), priceMap.get(p.getId())))
                .collect(Collectors.toList());

        return ProductPageResponse.builder()
                .total(page.getTotalElements())
                .page(request.getPage())
                .size(request.getSize())
                .items(items)
                .build();
    }

    // ── 일반 유저: 상품 상세 조회 ─────────────────────────────────────────────────

    @Transactional
    public ProductDetailResponse getProduct(Long productId, Long userId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!product.isActive()) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        product.increaseViewCount();

        ProductNutrient nutrient = productNutrientRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        CoupangLink coupangLink = coupangLinkRepository.findByProduct(product).orElse(null);

        // 목표별 사전계산 등급 단건 조회 — 열량구간은 1·2차 2000 고정
        int eerBand = EerBand.DEFAULT.kcal();
        GoalType goal = gradeLookupService.resolveGoalType(userId);
        GradeLookupService.GradeView gradeView = gradeLookupService.lookup(product.getId(), goal).orElse(null);
        ProductDetailResponse.PnsInfo pnsInfo = buildPnsInfo(product, gradeView, eerBand);

        // 영양소 바 차트 기준값 — 로그인 유저는 실제 EER, 비로그인은 eerBand(2000) 사용
        double eer = resolveEer(userId, eerBand);
        Long parentCategoryId = product.getCategory().getParent() == null
                ? null : product.getCategory().getParent().getId();
        ProductDetailResponse.NutrientBounds nutrientBounds = buildNutrientBounds(eer, parentCategoryId);

        return toDetailResponse(product, nutrient, userId, coupangLink, pnsInfo, nutrientBounds);
    }

    private ProductDetailResponse.PnsInfo buildPnsInfo(Product product,
                                                       GradeLookupService.GradeView result,
                                                       int eerBand) {
        if (result == null) return null;

        Category parent = product.getCategory().getParent();
        Long parentId     = parent == null ? null : parent.getId();
        String parentName = parent == null ? null : parent.getName();
        int total = gradeLookupService.countActiveByParentCategory(parentId);

        return ProductDetailResponse.PnsInfo.builder()
                .score(result.score())
                .grade(result.grade())
                .percentile(result.percentile())
                .topPercent(result.topPercent())
                .parentCategoryId(parentId)
                .parentCategoryName(parentName)
                .categoryTotal(total)
                .eerBand(eerBand)
                .build();
    }

    /**
     * 로그인 유저면 user_nutrition에서 실제 EER을 읽고,
     * 없거나 비로그인이면 eerBand(기본 2000)를 그대로 사용.
     */
    private double resolveEer(Long userId, int eerBand) {
        if (userId == null) return eerBand;
        return userNutritionRepository.findByUserId(userId)
                .map(UserNutrition::getEer)
                .filter(e -> e != null)
                .map(BigDecimal::doubleValue)
                .orElse((double) eerBand);
    }

    /** 영양소 바 차트 기준값 계산 — NutrientBounds */
    private ProductDetailResponse.NutrientBounds buildNutrientBounds(double eer, Long parentCategoryId) {
        // mealRatio: 대분류 카테고리 무관, 고정값 0.3 (MealRatioResolver 제거)
        double mealRatio = 0.3;
        return ProductDetailResponse.NutrientBounds.builder()
                .eerUsed(eer)
                .mealRatio(mealRatio)
                .caloriesMax(eer * mealRatio)
                .carbMin(eer * 0.50 / 4 * mealRatio)
                .carbMax(eer * 0.65 / 4 * mealRatio)
                .proteinMin(eer * 0.10 / 4 * mealRatio)
                .proteinMax(eer * 0.20 / 4 * mealRatio)
                .fatMin(eer * 0.15 / 9 * mealRatio)
                .fatMax(eer * 0.30 / 9 * mealRatio)
                .sugarMax(eer * 0.10 / 4 * mealRatio)
                .saturatedFatMax(eer * 0.07 / 9 * mealRatio)
                .transFatMax(eer * 0.01 / 9 * mealRatio)
                .cholesterolMax(300.0 * mealRatio)
                .sodiumMax(2300.0 * mealRatio)
                .fiberTarget(eer * 12.0 / 1000 * mealRatio)
                .build();
    }

    // ── 관리자: 상품 목록 조회 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminProductPageResponse getAdminProducts(AdminProductSearchRequest request) {

        if (request.getPage() < 0 || request.getSize() <= 0) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM);
        }
        if (request.getLinkStatus() != null && !ALLOWED_LINK_STATUS.contains(request.getLinkStatus())) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM);
        }

        Specification<Product> spec = Specification.where(ProductSpecification.isActiveAdmin(request.getIsActive()));
        spec = andIf(spec, request.getKeyword() != null, ProductSpecification.hasKeyword(request.getKeyword()));
        spec = andIf(spec, request.getCategoryIds() != null && !request.getCategoryIds().isEmpty(), ProductSpecification.hasCategory(request.getCategoryIds()));
        spec = andIf(spec, request.getBrandIds() != null && !request.getBrandIds().isEmpty(), ProductSpecification.hasBrand(request.getBrandIds()));
        spec = andIf(spec, request.getLinkStatus() != null, ProductSpecification.hasLinkStatus(request.getLinkStatus()));

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> page = productRepository.findAll(spec, pageable);

        List<AdminProductPageResponse.AdminProductListResponse> items = page.getContent().stream()
                .map(this::toAdminListResponse)
                .collect(Collectors.toList());

        return AdminProductPageResponse.builder()
                .total(page.getTotalElements())
                .page(request.getPage())
                .size(request.getSize())
                .items(items)
                .build();
    }

    // ── 관리자: 상품 수정 ─────────────────────────────────────────────────────────

    @Transactional
    public void updateProduct(Long productId, AdminProductUpdateRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 카테고리입니다."))
                : product.getCategory();

        Brand brand = request.getBrandId() != null
                ? brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 브랜드입니다."))
                : product.getBrand();

        String name      = request.getName()           != null ? request.getName()           : product.getName();
        String imageUrl  = request.getImageUrl()       != null ? request.getImageUrl()       : product.getImageUrl();
        BigDecimal score = request.getNutritionScore() != null ? request.getNutritionScore() : product.getNutritionScore();

        product.update(name, category, brand, imageUrl, score);

        if (request.getIsActive() != null) {
            if (request.getIsActive()) product.activate();
            else product.deactivate();
        }
    }

    // ── 관리자: 상품 비활성화 ─────────────────────────────────────────────────────

    @Transactional
    public void deactivateProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!product.isActive()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "이미 비활성화된 상품입니다.");
        }

        product.deactivate();
    }

    // ── 관리자: 전체 초기화 ───────────────────────────────────────────────────────

    @Transactional
    public void resetAll() {
        entityManager.createNativeQuery("TRUNCATE TABLE product_grades").executeUpdate();   // 사전계산 등급 — products FK 가 없어 명시적으로 비운다
        entityManager.createNativeQuery("TRUNCATE TABLE user_favorites RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE review_images RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE reviews RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE coupang_links RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_view_logs RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE product_nutrients RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE products RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE brands RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM categories WHERE depth = 3").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM categories WHERE depth = 2").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM categories WHERE depth = 1").executeUpdate();
        entityManager.createNativeQuery("ALTER SEQUENCE categories_id_seq RESTART WITH 1").executeUpdate();
    }

    // ── SCORE 정렬 전용 쿼리 ──────────────────────────────────────────────────────

    /**
     * 목표별 사전계산 등급 점수(product_grades.score) JOIN 후 내림차순 — 로그인·비로그인 같은 조인, 목표만 다르다
     * (비로그인·미설정은 GENERAL, 슬롯 0). 사전계산이 없는 제품(영양정보 부족)은 이 정렬에서 빠진다(구 동작과 동일).
     * 필터 조건(카테고리/브랜드/영양소 등)은 동적 WHERE절로 처리
     */
    @SuppressWarnings("unchecked")
    private Page<Product> findAllOrderByGradeScore(ProductSearchRequest request,
                                                   GoalType goal,
                                                   List<Long> claimFilteredIds) {
        int page = request.getPage();
        int size = request.getSize();

        // 동적 WHERE 조건 조립
        StringBuilder where = new StringBuilder("WHERE p.is_active = TRUE ");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();

        // 카테고리/브랜드/nutrientClaims 필터 결과 ID 목록
        // Long[] named parameter → PostgreSQL bigint[] 자동변환 안 됨 → ID를 직접 IN절에 삽입 (SQL injection 안전: Long 타입)
        if (claimFilteredIds != null) {
            if (claimFilteredIds.isEmpty()) {
                return new org.springframework.data.domain.PageImpl<>(
                        List.of(), PageRequest.of(page, size), 0);
            }
            String idList = claimFilteredIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            where.append("AND p.id IN (").append(idList).append(") ");
        }

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            where.append("AND p.name ILIKE :keyword ");
            params.put("keyword", "%" + request.getKeyword() + "%");
        }
        if (request.getMinCalories() != null) {
            where.append("AND pn.calories >= :minCalories ");
            params.put("minCalories", request.getMinCalories());
        }
        if (request.getMaxCalories() != null) {
            where.append("AND pn.calories <= :maxCalories ");
            params.put("maxCalories", request.getMaxCalories());
        }
        if (request.getMinProtein() != null) {
            where.append("AND pn.protein >= :minProtein ");
            params.put("minProtein", request.getMinProtein());
        }
        if (request.getMaxProtein() != null) {
            where.append("AND pn.protein <= :maxProtein ");
            params.put("maxProtein", request.getMaxProtein());
        }
        if (request.getMinFat() != null) {
            where.append("AND pn.fat >= :minFat ");
            params.put("minFat", request.getMinFat());
        }
        if (request.getMaxFat() != null) {
            where.append("AND pn.fat <= :maxFat ");
            params.put("maxFat", request.getMaxFat());
        }
        if (request.getMinCarbohydrate() != null) {
            where.append("AND pn.carbohydrate >= :minCarbohydrate ");
            params.put("minCarbohydrate", request.getMinCarbohydrate());
        }
        if (request.getMaxCarbohydrate() != null) {
            where.append("AND pn.carbohydrate <= :maxCarbohydrate ");
            params.put("maxCarbohydrate", request.getMaxCarbohydrate());
        }
        if (request.getMinSugar() != null) {
            where.append("AND pn.sugar >= :minSugar ");
            params.put("minSugar", request.getMinSugar());
        }
        if (request.getMaxSugar() != null) {
            where.append("AND pn.sugar <= :maxSugar ");
            params.put("maxSugar", request.getMaxSugar());
        }
        if (request.getMinSodium() != null) {
            where.append("AND pn.sodium >= :minSodium ");
            params.put("minSodium", request.getMinSodium());
        }
        if (request.getMaxSodium() != null) {
            where.append("AND pn.sodium <= :maxSodium ");
            params.put("maxSodium", request.getMaxSodium());
        }
        if (request.getMinNutritionScore() != null) {
            where.append("AND p.nutrition_score >= :minNutritionScore ");
            params.put("minNutritionScore", request.getMinNutritionScore());
        }
        if (request.getMaxNutritionScore() != null) {
            where.append("AND p.nutrition_score <= :maxNutritionScore ");
            params.put("maxNutritionScore", request.getMaxNutritionScore());
        }

        // 영양소 조인 필요 여부
        boolean needNutrientJoin = request.getMinCalories() != null || request.getMaxCalories() != null
                || request.getMinProtein() != null || request.getMaxProtein() != null
                || request.getMinFat() != null || request.getMaxFat() != null
                || request.getMinCarbohydrate() != null || request.getMaxCarbohydrate() != null
                || request.getMinSugar() != null || request.getMaxSugar() != null
                || request.getMinSodium() != null || request.getMaxSodium() != null;

        String nutrientJoin = needNutrientJoin
                ? "JOIN product_nutrients pn ON p.id = pn.product_id "
                : "";

        String gradeJoin = "JOIN product_grades pg ON p.id = pg.product_id AND pg.eer_band = :eerBand AND pg.goal = :goal ";
        String orderBy = "ORDER BY pg.score DESC NULLS LAST ";
        params.put("eerBand", EerBand.defaultSlot(goal));
        params.put("goal", goal.name());

        String sql = "SELECT p.* FROM products p " + gradeJoin + nutrientJoin + where + orderBy
                + "LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM products p " + gradeJoin + nutrientJoin + where;

        params.put("limit", size);
        params.put("offset", (long) page * size);

        var query = entityManager.createNativeQuery(sql, Product.class);
        var countQuery = entityManager.createNativeQuery(countSql);
        params.forEach((k, v) -> {
            if (!k.equals("limit") && !k.equals("offset")) {
                countQuery.setParameter(k, v);
            }
            query.setParameter(k, v);
        });

        List<Product> content = query.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, size), total.longValue());
    }

    // ── ACCURACY 정렬 전용 쿼리 ────────────────────────────────────────────────────

    /**
     * 정확도순(ACCURACY) 정렬 — PostgreSQL pg_trgm 기반 상품명 트라이그램 유사도로 정렬.
     * 호출부에서 키워드가 있을 때만 이 메서드를 호출하도록 보장함.
     */
    @SuppressWarnings("unchecked")
    private Page<Product> findAllOrderByRelevance(ProductSearchRequest request,
                                                  List<Long> claimFilteredIds) {
        int page = request.getPage();
        int size = request.getSize();

        StringBuilder where = new StringBuilder("WHERE p.is_active = TRUE ");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();

        if (claimFilteredIds != null) {
            if (claimFilteredIds.isEmpty()) {
                return new org.springframework.data.domain.PageImpl<>(
                        List.of(), PageRequest.of(page, size), 0);
            }
            String idList = claimFilteredIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            where.append("AND p.id IN (").append(idList).append(") ");
        }

        params.put("keyword", request.getKeyword());

        if (request.getMinCalories() != null) {
            where.append("AND pn.calories >= :minCalories ");
            params.put("minCalories", request.getMinCalories());
        }
        if (request.getMaxCalories() != null) {
            where.append("AND pn.calories <= :maxCalories ");
            params.put("maxCalories", request.getMaxCalories());
        }
        if (request.getMinProtein() != null) {
            where.append("AND pn.protein >= :minProtein ");
            params.put("minProtein", request.getMinProtein());
        }
        if (request.getMaxProtein() != null) {
            where.append("AND pn.protein <= :maxProtein ");
            params.put("maxProtein", request.getMaxProtein());
        }
        if (request.getMinFat() != null) {
            where.append("AND pn.fat >= :minFat ");
            params.put("minFat", request.getMinFat());
        }
        if (request.getMaxFat() != null) {
            where.append("AND pn.fat <= :maxFat ");
            params.put("maxFat", request.getMaxFat());
        }
        if (request.getMinCarbohydrate() != null) {
            where.append("AND pn.carbohydrate >= :minCarbohydrate ");
            params.put("minCarbohydrate", request.getMinCarbohydrate());
        }
        if (request.getMaxCarbohydrate() != null) {
            where.append("AND pn.carbohydrate <= :maxCarbohydrate ");
            params.put("maxCarbohydrate", request.getMaxCarbohydrate());
        }
        if (request.getMinSugar() != null) {
            where.append("AND pn.sugar >= :minSugar ");
            params.put("minSugar", request.getMinSugar());
        }
        if (request.getMaxSugar() != null) {
            where.append("AND pn.sugar <= :maxSugar ");
            params.put("maxSugar", request.getMaxSugar());
        }
        if (request.getMinSodium() != null) {
            where.append("AND pn.sodium >= :minSodium ");
            params.put("minSodium", request.getMinSodium());
        }
        if (request.getMaxSodium() != null) {
            where.append("AND pn.sodium <= :maxSodium ");
            params.put("maxSodium", request.getMaxSodium());
        }
        if (request.getMinNutritionScore() != null) {
            where.append("AND p.nutrition_score >= :minNutritionScore ");
            params.put("minNutritionScore", request.getMinNutritionScore());
        }
        if (request.getMaxNutritionScore() != null) {
            where.append("AND p.nutrition_score <= :maxNutritionScore ");
            params.put("maxNutritionScore", request.getMaxNutritionScore());
        }

        boolean needNutrientJoin = request.getMinCalories() != null || request.getMaxCalories() != null
                || request.getMinProtein() != null || request.getMaxProtein() != null
                || request.getMinFat() != null || request.getMaxFat() != null
                || request.getMinCarbohydrate() != null || request.getMaxCarbohydrate() != null
                || request.getMinSugar() != null || request.getMaxSugar() != null
                || request.getMinSodium() != null || request.getMaxSodium() != null;

        String nutrientJoin = needNutrientJoin
                ? "JOIN product_nutrients pn ON p.id = pn.product_id "
                : "";

        // similarity(name, keyword): pg_trgm 트라이그램 유사도(0~1), 높을수록 관련도 높음
        String sql = "SELECT p.* FROM products p " + nutrientJoin + where
                + "ORDER BY similarity(p.name, :keyword) DESC, p.created_at DESC "
                + "LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM products p " + nutrientJoin + where;

        params.put("limit", size);
        params.put("offset", (long) page * size);

        var query = entityManager.createNativeQuery(sql, Product.class);
        var countQuery = entityManager.createNativeQuery(countSql);
        params.forEach((k, v) -> {
            // keyword는 countSql에 존재하지 않는 파라미터(ORDER BY의 similarity()에서만 사용)이므로
            // countQuery에 바인딩하면 "No parameter named ':keyword'" 예외가 발생함. 반드시 제외.
            if (!k.equals("limit") && !k.equals("offset") && !k.equals("keyword")) {
                countQuery.setParameter(k, v);
            }
            query.setParameter(k, v);
        });

        List<Product> content = query.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, size), total.longValue());
    }

    // ── RECOMMENDED 정렬 전용 쿼리 ─────────────────────────────────────────────────

    /**
     * 추천순(RECOMMENDED) 정렬 — 새 테이블 없이 기존 recommendation_cache(CF), product_vectors_by_diet(CONTENT) 재사용.
     * 우선순위: CF 캐시 있으면 CF 점수 → 없고 diet_purpose 벡터 매칭 있으면 벡터 유사도 → 둘 다 없으면 인기순.
     * 호출부에서 userId != null일 때만 이 메서드를 호출하도록 보장함.
     */
    @SuppressWarnings("unchecked")
    private Page<Product> findAllOrderByPreference(ProductSearchRequest request, Long userId,
                                                   List<Long> claimFilteredIds) {
        int page = request.getPage();
        int size = request.getSize();

        StringBuilder where = new StringBuilder("WHERE p.is_active = TRUE ");
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();

        if (claimFilteredIds != null) {
            if (claimFilteredIds.isEmpty()) {
                return new org.springframework.data.domain.PageImpl<>(
                        List.of(), PageRequest.of(page, size), 0);
            }
            String idList = claimFilteredIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            where.append("AND p.id IN (").append(idList).append(") ");
        }

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            where.append("AND p.name ILIKE :keyword ");
            params.put("keyword", "%" + request.getKeyword() + "%");
        }
        if (request.getMinCalories() != null) {
            where.append("AND pn.calories >= :minCalories ");
            params.put("minCalories", request.getMinCalories());
        }
        if (request.getMaxCalories() != null) {
            where.append("AND pn.calories <= :maxCalories ");
            params.put("maxCalories", request.getMaxCalories());
        }
        if (request.getMinProtein() != null) {
            where.append("AND pn.protein >= :minProtein ");
            params.put("minProtein", request.getMinProtein());
        }
        if (request.getMaxProtein() != null) {
            where.append("AND pn.protein <= :maxProtein ");
            params.put("maxProtein", request.getMaxProtein());
        }
        if (request.getMinFat() != null) {
            where.append("AND pn.fat >= :minFat ");
            params.put("minFat", request.getMinFat());
        }
        if (request.getMaxFat() != null) {
            where.append("AND pn.fat <= :maxFat ");
            params.put("maxFat", request.getMaxFat());
        }
        if (request.getMinCarbohydrate() != null) {
            where.append("AND pn.carbohydrate >= :minCarbohydrate ");
            params.put("minCarbohydrate", request.getMinCarbohydrate());
        }
        if (request.getMaxCarbohydrate() != null) {
            where.append("AND pn.carbohydrate <= :maxCarbohydrate ");
            params.put("maxCarbohydrate", request.getMaxCarbohydrate());
        }
        if (request.getMinSugar() != null) {
            where.append("AND pn.sugar >= :minSugar ");
            params.put("minSugar", request.getMinSugar());
        }
        if (request.getMaxSugar() != null) {
            where.append("AND pn.sugar <= :maxSugar ");
            params.put("maxSugar", request.getMaxSugar());
        }
        if (request.getMinSodium() != null) {
            where.append("AND pn.sodium >= :minSodium ");
            params.put("minSodium", request.getMinSodium());
        }
        if (request.getMaxSodium() != null) {
            where.append("AND pn.sodium <= :maxSodium ");
            params.put("maxSodium", request.getMaxSodium());
        }
        if (request.getMinNutritionScore() != null) {
            where.append("AND p.nutrition_score >= :minNutritionScore ");
            params.put("minNutritionScore", request.getMinNutritionScore());
        }
        if (request.getMaxNutritionScore() != null) {
            where.append("AND p.nutrition_score <= :maxNutritionScore ");
            params.put("maxNutritionScore", request.getMaxNutritionScore());
        }

        boolean needNutrientJoin = request.getMinCalories() != null || request.getMaxCalories() != null
                || request.getMinProtein() != null || request.getMaxProtein() != null
                || request.getMinFat() != null || request.getMaxFat() != null
                || request.getMinCarbohydrate() != null || request.getMaxCarbohydrate() != null
                || request.getMinSugar() != null || request.getMaxSugar() != null
                || request.getMinSodium() != null || request.getMaxSodium() != null;

        String nutrientJoin = needNutrientJoin
                ? "JOIN product_nutrients pn ON p.id = pn.product_id "
                : "";

        // 1단계: CF 캐시 존재 여부 확인 (recommendation_cache)
        boolean hasCfCache = !recommendationCacheRepository.findByUserIdOrderByScoreDesc(userId).isEmpty();

        String preferenceJoin;
        String orderBy;

        if (hasCfCache) {
            preferenceJoin = "LEFT JOIN recommendation_cache rc ON p.id = rc.product_id AND rc.user_id = :userId ";
            orderBy = "ORDER BY rc.score DESC NULLS LAST, p.view_count DESC ";
            params.put("userId", userId);
        } else {
            String dietPurpose = userNutritionRepository.findByUserId(userId)
                    .map(UserNutrition::getDietPurpose)
                    .orElse(null);

            if (dietPurpose != null) {
                // 2단계: 콘텐츠 기반 — diet_purpose 영양소 벡터 유사도 (product_vectors_by_diet)
                String targetVector = recommendationService.resolveTargetVector(dietPurpose);
                preferenceJoin = "LEFT JOIN product_vectors_by_diet pvbd "
                        + "ON p.id = pvbd.product_id AND pvbd.diet_purpose = :dietPurpose ";
                orderBy = "ORDER BY (pvbd.weighted_vector <=> CAST(:targetVector AS vector)) ASC NULLS LAST, "
                        + "p.view_count DESC ";
                params.put("dietPurpose", dietPurpose);
                params.put("targetVector", targetVector);
            } else {
                // 3단계: CF도 콘텐츠 벡터 신호도 없음 → 인기순 폴백
                preferenceJoin = "";
                orderBy = "ORDER BY p.view_count DESC ";
            }
        }

        String sql = "SELECT p.* FROM products p " + preferenceJoin + nutrientJoin + where + orderBy
                + "LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM products p " + preferenceJoin + nutrientJoin + where;

        params.put("limit", size);
        params.put("offset", (long) page * size);

        var query = entityManager.createNativeQuery(sql, Product.class);
        var countQuery = entityManager.createNativeQuery(countSql);
        params.forEach((k, v) -> {
            if (!k.equals("limit") && !k.equals("offset")) {
                countQuery.setParameter(k, v);
            }
            query.setParameter(k, v);
        });

        List<Product> content = query.getResultList();
        Number total = (Number) countQuery.getSingleResult();
        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, size), total.longValue());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private Specification<Product> andIf(Specification<Product> spec, boolean condition, Specification<Product> other) {
        return condition ? spec.and(other) : spec;
    }

    private void validateMinMax(ProductSearchRequest req) {
        if (isInvalid(req.getMinCalories(),      req.getMaxCalories())      ||
                isInvalid(req.getMinProtein(),        req.getMaxProtein())       ||
                isInvalid(req.getMinFat(),            req.getMaxFat())           ||
                isInvalid(req.getMinCarbohydrate(),   req.getMaxCarbohydrate())  ||
                isInvalid(req.getMinSugar(),          req.getMaxSugar())         ||
                isInvalid(req.getMinSodium(),         req.getMaxSodium())        ||
                isInvalid(req.getMinNutritionScore(), req.getMaxNutritionScore())) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM);
        }
    }

    private boolean isInvalid(BigDecimal min, BigDecimal max) {
        return min != null && max != null && min.compareTo(max) > 0;
    }

    private List<NutrientClaim> parseClaims(List<String> rawClaims) {
        if (rawClaims == null || rawClaims.isEmpty()) return List.of();
        return rawClaims.stream()
                .map(s -> {
                    try {
                        return NutrientClaim.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new CustomException(ErrorCode.INVALID_QUERY_PARAM,
                                "유효하지 않은 영양 강조표시 값: " + s);
                    }
                })
                .collect(Collectors.toList());
    }

    private Sort resolveSort(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.DESC, "createdAt");
        return switch (sort) {
            case "POPULAR"     -> Sort.by(Sort.Direction.DESC, "viewCount");
            case "SCORE"       -> Sort.by(Sort.Direction.DESC, "nutritionScore");
            case "ACCURACY"    -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "RECOMMENDED" -> Sort.by(Sort.Direction.DESC, "viewCount");
            default            -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    // ── 변환 메서드 ───────────────────────────────────────────────────────────────

    private ProductListResponse toListResponse(Product product, Long userId, String grade, Integer price) {
        boolean favorited = userId != null &&
                savedProductRepository.existsByUserIdAndProductIdAndProductIsActiveTrue(userId, product.getId());

        return ProductListResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .nutritionScore(product.getNutritionScore())
                .grade(grade)
                .price(price)
                .isFavorited(favorited)
                .brand(product.getBrand() == null ? null : ProductListResponse.BrandInfo.builder()
                        .id(product.getBrand().getId())
                        .name(product.getBrand().getName())
                        .build())
                .category(ProductListResponse.CategoryInfo.builder()
                        .id(product.getCategory().getId())
                        .name(product.getCategory().getName())
                        .build())
                .build();
    }

    private ProductDetailResponse toDetailResponse(Product product, ProductNutrient nutrient,
                                                   Long userId, CoupangLink coupangLink,
                                                   ProductDetailResponse.PnsInfo pnsInfo,
                                                   ProductDetailResponse.NutrientBounds nutrientBounds) {
        boolean favorited = userId != null &&
                savedProductRepository.existsByUserIdAndProductIdAndProductIsActiveTrue(userId, product.getId());

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .nutritionScore(product.getNutritionScore())
                .grade(pnsInfo != null ? pnsInfo.getGrade() : null)
                .viewCount(product.getViewCount())
                .isFavorited(favorited)
                .scoreRankPercent(null)
                .brand(product.getBrand() == null ? null : ProductDetailResponse.BrandInfo.builder()
                        .id(product.getBrand().getId())
                        .name(product.getBrand().getName())
                        .build())
                .category(ProductDetailResponse.CategoryInfo.builder()
                        .id(product.getCategory().getId())
                        .name(product.getCategory().getName())
                        .build())
                .nutrients(ProductDetailResponse.NutrientInfo.builder()
                        .servingSize(nutrient.getServingSize())
                        .calories(nutrient.getCalories())
                        .carbohydrate(nutrient.getCarbohydrate())
                        .sugar(nutrient.getSugar())
                        .protein(nutrient.getProtein())
                        .fat(nutrient.getFat())
                        .saturatedFat(nutrient.getSaturatedFat())
                        .transFat(nutrient.getTransFat())
                        .cholesterol(nutrient.getCholesterol())
                        .sodium(nutrient.getSodium())
                        .build())
                .coupang(coupangLink == null || !"LINKED".equals(coupangLink.getLinkStatus())
                        ? null
                        : ProductDetailResponse.CoupangInfo.builder()
                        .affiliateUrl(coupangLink.getAffiliateUrl())
                        .landingUrl(coupangLink.getLandingUrl())
                        .price(coupangLink.getProductPrice())
                        .isRocket(coupangLink.getIsRocket())
                        .isFreeShipping(coupangLink.getIsFreeShipping())
                        .lastSyncedAt(coupangLink.getLastSyncedAt())
                        .build())
                .pns(pnsInfo)
                .nutrientBounds(nutrientBounds)
                .build();
    }

    private AdminProductPageResponse.AdminProductListResponse toAdminListResponse(Product product) {
        return AdminProductPageResponse.AdminProductListResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .isActive(product.isActive())
                .nutritionScore(product.getNutritionScore())
                .linkStatus(product.getCoupangLink() != null
                        ? product.getCoupangLink().getLinkStatus()
                        : "UNLINKED")
                .brand(product.getBrand() == null ? null : AdminProductPageResponse.BrandInfo.builder()
                        .id(product.getBrand().getId())
                        .name(product.getBrand().getName())
                        .build())
                .category(AdminProductPageResponse.CategoryInfo.builder()
                        .id(product.getCategory().getId())
                        .name(product.getCategory().getName())
                        .build())
                .build();
    }
}