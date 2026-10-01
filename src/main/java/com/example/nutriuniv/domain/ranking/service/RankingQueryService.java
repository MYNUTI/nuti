package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.entity.GradeCopy;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.grade.entity.ProductGrade;
import com.example.nutriuniv.domain.grade.repository.GradeCopyRepository;
import com.example.nutriuniv.domain.grade.repository.ProductGradeRepository;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.ranking.dto.AdminRankingCategoriesResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingCategoriesResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingCriteriaResponse;
import com.example.nutriuniv.domain.ranking.dto.RankingResponse;
import com.example.nutriuniv.domain.ranking.entity.CategoryRanking;
import com.example.nutriuniv.domain.ranking.entity.RankingCategory;
import com.example.nutriuniv.domain.ranking.entity.RankingCategoryStat;
import com.example.nutriuniv.domain.ranking.repository.CategoryRankingRepository;
import com.example.nutriuniv.domain.ranking.repository.RankingCategoryMappingRepository;
import com.example.nutriuniv.domain.ranking.repository.RankingCategoryRepository;
import com.example.nutriuniv.domain.ranking.repository.RankingCategoryStatRepository;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.search.util.SearchHighlight;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 랭킹 조회 (기능명세서 6.3) — 배치 결과(category_rankings·ranking_category_stats)를 읽기만 한다.
 * 목표를 안 주면 내 목표(없으면 일반). 게이트를 못 넘은 분류는 목록에 없고 직접 부르면 404 RANKING_NOT_OPEN.
 */
@Service
public class RankingQueryService {

    public static final int MAX_PAGE_SIZE = 50;

    private final RankingCategoryRepository categoryRepository;
    private final RankingCategoryStatRepository statRepository;
    private final RankingCategoryMappingRepository mappingRepository;
    private final CategoryRankingRepository rankingRepository;
    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final ProductGradeRepository productGradeRepository;
    private final SavedProductRepository savedProductRepository;
    private final GradeCopyRepository copyRepository;
    private final GradeLookupService gradeLookupService;
    private final OwnerResolver ownerResolver;
    private final RankingBatchService batchService;
    private final int maxRank;

    public RankingQueryService(RankingCategoryRepository categoryRepository,
                               RankingCategoryStatRepository statRepository,
                               RankingCategoryMappingRepository mappingRepository,
                               CategoryRankingRepository rankingRepository,
                               ProductRepository productRepository,
                               ProductNutrientRepository productNutrientRepository,
                               ProductGradeRepository productGradeRepository,
                               SavedProductRepository savedProductRepository,
                               GradeCopyRepository copyRepository,
                               GradeLookupService gradeLookupService,
                               OwnerResolver ownerResolver,
                               RankingBatchService batchService,
                               @Value("${app.ranking.max-rank:200}") int maxRank) {
        this.categoryRepository = categoryRepository;
        this.statRepository = statRepository;
        this.mappingRepository = mappingRepository;
        this.rankingRepository = rankingRepository;
        this.productRepository = productRepository;
        this.productNutrientRepository = productNutrientRepository;
        this.productGradeRepository = productGradeRepository;
        this.savedProductRepository = savedProductRepository;
        this.copyRepository = copyRepository;
        this.gradeLookupService = gradeLookupService;
        this.ownerResolver = ownerResolver;
        this.batchService = batchService;
        this.maxRank = maxRank;
    }

    // ── GET /rankings/categories ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RankingCategoriesResponse categories() {
        List<RankingCategory> categories = categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();
        Map<Long, RankingCategoryStat> stats = statsOf(categories);

        List<RankingCategory> open = categories.stream()
                .filter(c -> stats.containsKey(c.getId()) && stats.get(c.getId()).isGatePassed())
                .toList();
        Long defaultId = open.stream().filter(RankingCategory::isDefault).map(RankingCategory::getId).findFirst()
                .orElse(open.isEmpty() ? null : open.get(0).getId());          // 지정이 없으면 첫 항목

        List<RankingCategoriesResponse.Item> items = open.stream()
                .map(c -> {
                    RankingCategoryStat s = stats.get(c.getId());
                    return RankingCategoriesResponse.Item.builder()
                            .categoryId(c.getId())
                            .name(c.getName())
                            .productCount(s.getTotalCount())
                            .analyzedCount(s.getAnalyzedCount())
                            .analyzedRatio(s.analyzedRatio())
                            .isDefault(c.getId().equals(defaultId))
                            .build();
                })
                .toList();

        LocalDateTime updatedAt = open.stream().map(c -> stats.get(c.getId()).getComputedAt())
                .max(Comparator.naturalOrder()).orElse(null);
        return RankingCategoriesResponse.builder().items(items).updatedAt(updatedAt).build();
    }

    // ── GET /rankings ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RankingResponse rankings(Long categoryId, String goalParam, int page, int size, Actor actor) {
        if (categoryId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "categoryId는 필수입니다.");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        RankingCategory category = categoryRepository.findByIdAndIsActiveTrue(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 랭킹 분류입니다."));
        RankingCategoryStat stat = statRepository.findById(categoryId).orElse(null);
        if (stat == null || !stat.isGatePassed()) {
            throw new CustomException(ErrorCode.RANKING_NOT_OPEN);
        }

        Owner owner = ownerResolver.resolveOrNull(actor);
        GoalType goal = goalParam != null && !goalParam.isBlank()
                ? GoalType.from(goalParam)
                : gradeLookupService.resolveGoalType(owner);

        long total = rankingRepository.countByRankingCategoryIdAndGoal(categoryId, goal);
        List<CategoryRanking> rows = rankingRepository.findByRankingCategoryIdAndGoalOrderByRankAsc(categoryId, goal, PageRequest.of(page, size));

        List<Long> ids = rows.stream().map(CategoryRanking::getProductId).toList();
        Map<Long, Product> products = ids.isEmpty() ? Map.of()
                : productRepository.findByIdInAndIsActiveTrue(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductNutrient> nutrients = ids.isEmpty() ? Map.of()
                : productNutrientRepository.findAllById(ids).stream().collect(Collectors.toMap(ProductNutrient::getId, Function.identity()));
        Map<Long, ProductGrade> grades = ids.isEmpty() ? Map.of()
                : productGradeRepository.findByProductIdInAndGoalAndEerBand(ids, goal, EerBand.defaultSlot(goal)).stream()
                        .collect(Collectors.toMap(ProductGrade::getProductId, Function.identity()));
        Set<Long> savedIds = owner == null ? Set.of()
                : owner.isUser() ? savedProductRepository.findSavedProductIdsByUserId(owner.userId())
                : savedProductRepository.findSavedProductIdsByAnonymousId(owner.anonymousId());

        List<RankingResponse.Item> items = new ArrayList<>(rows.size());
        for (CategoryRanking r : rows) {
            Product p = products.get(r.getProductId());
            if (p == null) continue;                                          // 배치 뒤 비활성화된 제품 — 다음 배치에서 빠진다
            String grade = r.getGrade().name();
            items.add(RankingResponse.Item.builder()
                    .rank(r.getRank())
                    .productId(p.getId())
                    .name(p.getName())
                    .brandName(p.getBrand() == null ? null : p.getBrand().getName())
                    .imageUrl(p.getImageUrl())
                    .grade(grade)
                    .gradeLabel(gradeLookupService.label(goal, grade))
                    .highlights(highlights(goal, grades.get(p.getId()), nutrients.get(p.getId())))
                    .isSaved(savedIds.contains(p.getId()))
                    .build());
        }

        return RankingResponse.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .appliedGoal(goal.name())
                .coverage(RankingResponse.Coverage.builder()
                        .totalCount(stat.getTotalCount())
                        .analyzedCount(stat.getAnalyzedCount())
                        .ratio(stat.analyzedRatio())
                        .build())
                .updatedAt(stat.getComputedAt())
                .totalRanked(total)
                .page(page)
                .size(size)
                .hasNext((long) (page + 1) * size < total)
                .items(items)
                .build();
    }

    /** 목표 기준 위반 문구 — 검색 결과와 같은 규칙(SearchHighlight). 감점 요인이 없으면 빈 배열. */
    private static List<String> highlights(GoalType goal, ProductGrade grade, ProductNutrient n) {
        if (grade == null || grade.getTopPenaltyNutrient() == null || n == null) return List.of();
        Nutrient nutrient = grade.getTopPenaltyNutrient();
        String line = SearchHighlight.build(goal, nutrient, per100g(n, nutrient));
        return line == null ? List.of() : List.of(line);
    }

    private static BigDecimal per100g(ProductNutrient n, Nutrient nutrient) {
        return switch (nutrient) {
            case CALORIES      -> n.getCaloriesPer100g();
            case PROTEIN       -> n.getProteinPer100g();
            case DIETARY_FIBER -> n.getFiberPer100g();
            case SUGAR         -> n.getSugarPer100g();
            case SATURATED_FAT -> n.getSaturatedFatPer100g();
            case TRANS_FAT     -> n.getTransFatPer100g();
            case CHOLESTEROL   -> n.getCholesterolPer100g();
            case SODIUM        -> n.getSodiumPer100g();
        };
    }

    // ── GET /rankings/criteria ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RankingCriteriaResponse criteria(Long categoryId) {
        List<RankingCriteriaResponse.Section> sections = new ArrayList<>();
        if (categoryId != null) {
            RankingCategory category = categoryRepository.findByIdAndIsActiveTrue(categoryId)
                    .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 랭킹 분류입니다."));
            RankingCategoryStat stat = statRepository.findById(categoryId).orElse(null);
            sections.add(RankingCriteriaResponse.Section.builder()
                    .title("「" + category.getName() + "」 현황")
                    .body(coverageSentence(category, stat))
                    .build());
        }

        String version = null;
        LocalDateTime updatedAt = null;
        for (String code : RankingCopySeeder.CODES) {
            for (GradeCopy c : copyRepository.findByCopyCodeOrderByDisplayOrderAsc(code)) {
                if (c.getGoal() != null) continue;
                sections.add(RankingCriteriaResponse.Section.builder().title(c.getTitle()).body(c.getBody()).build());
                if (version == null && c.getVersion() != null) version = c.getVersion();
                if (updatedAt == null || (c.getUpdatedAt() != null && c.getUpdatedAt().isAfter(updatedAt))) updatedAt = c.getUpdatedAt();
            }
        }
        return RankingCriteriaResponse.builder()
                .version(version != null ? version : RankingCopySeeder.VERSION)
                .updatedAt(updatedAt)
                .sections(sections)
                .build();
    }

    /** 「단백질 음료 128개 중 121개 분석 완료 (95%)」 + 열림/닫힘. */
    static String coverageSentence(RankingCategory category, RankingCategoryStat stat) {
        if (stat == null) {
            return category.getName() + " 분류는 아직 집계 전이에요. 매일 새벽 4시에 갱신됩니다.";
        }
        long pct = Math.round(stat.analyzedRatio() * 100);
        String head = category.getName() + " " + stat.getTotalCount() + "개 중 " + stat.getAnalyzedCount() + "개 분석 완료 (" + pct + "%).";
        if (stat.isGatePassed()) {
            return head + " 분석 완료 제품을 등급순으로 보여드려요.";
        }
        return head + " 아직 순위를 열 조건(" + RankingGate.failureReason(stat.getAnalyzedCount(), stat.getACount(), stat.getDCount()) + ")이 안 돼 순위를 제공하지 않아요.";
    }

    // ── 관리자 ───────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminRankingCategoriesResponse adminCategories() {
        List<RankingCategory> categories = categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();
        Map<Long, RankingCategoryStat> stats = statsOf(categories);
        List<AdminRankingCategoriesResponse.Item> items = categories.stream().map(c -> {
            RankingCategoryStat s = stats.get(c.getId());
            return AdminRankingCategoriesResponse.Item.builder()
                    .categoryId(c.getId())
                    .name(c.getName())
                    .displayOrder(c.getDisplayOrder())
                    .isDefault(c.isDefault())
                    .mappingCount(mappingRepository.countByRankingCategoryId(c.getId()))
                    .totalCount(s == null ? 0 : s.getTotalCount())
                    .analyzedCount(s == null ? 0 : s.getAnalyzedCount())
                    .gradeACount(s == null ? 0 : s.getACount())
                    .gradeDCount(s == null ? 0 : s.getDCount())
                    .gatePassed(s != null && s.isGatePassed())
                    .gateFailureReason(s == null ? "아직 집계 전" : RankingGate.failureReason(s.getAnalyzedCount(), s.getACount(), s.getDCount()))
                    .rankedRows(s == null ? 0 : s.getRankedRows())
                    .computedAt(s == null ? null : s.getComputedAt())
                    .build();
        }).toList();
        return AdminRankingCategoriesResponse.builder()
                .items(items)
                .maxRank(maxRank)
                .rebuildRunning(batchService.isRunning())
                .build();
    }

    private Map<Long, RankingCategoryStat> statsOf(List<RankingCategory> categories) {
        if (categories.isEmpty()) return Map.of();
        return statRepository.findByRankingCategoryIdIn(categories.stream().map(RankingCategory::getId).toList()).stream()
                .collect(Collectors.toMap(RankingCategoryStat::getRankingCategoryId, Function.identity()));
    }
}
