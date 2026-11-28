package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.domain.body.service.KcalBandResolver;
import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.enums.FilterChip;
import com.example.nutriuniv.domain.product.enums.ListSort;
import com.example.nutriuniv.domain.product.enums.Seller;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.product.repository.ProductListQuery.Filters;
import com.example.nutriuniv.domain.product.repository.ProductListQuery.Scope;
import com.example.nutriuniv.domain.product.service.ProductCardService;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 (v3 SRCH_006 · SRCH_007 · SRCH_008) — 조회할 때 계산한다(필터 조합이 자유로워 미리 만든 순위 표를 쓰지 않는다).
 * <ul>
 *   <li>순위 대상: 분석 완료 · 생산 중. 광고 · 제휴 · 노출비는 순서에 넣지 않는다</li>
 *   <li>칩(중분류 · 합산 칩)은 분석 완료 300개 이상일 때만 연다. 대분류 15개는 늘 보인다</li>
 *   <li>숨긴 대분류 「기타」와 그 중분류는 어디에도 보이지 않는다(DATA_005). 그 제품은 순위 대상도 아니다(ProductListQuery)</li>
 *   <li>탭 순서: 그 칩에 보이는 제품(소속 + 같이 보이는 제품)이 많은 순, 같으면 시드 순서</li>
 *   <li>필터: 판매처 · 브랜드 · 영양성분 탭끼리 AND, 탭 안 값끼리 OR</li>
 *   <li>목표는 요청으로만 받는다(저장 · 기록하지 않음). 없으면 감량</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int OVERVIEW_TOP = 3;
    /** 랭킹 첫 화면 — 대분류마다 1위를 보일 중분류 수. */
    public static final int LEADER_CHIPS = 3;
    public static final int TOP_BRANDS = 8;

    private final KcalBandResolver kcalBandResolver;
    private final CategoryRepository categoryRepository;
    private final ChipStatsService chipStats;
    private final ProductListQuery listQuery;
    private final ProductCardService cardService;
    private final OwnerResolver ownerResolver;

    // ── GET /rankings/categories ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CategoriesResponse categories() {
        Tree tree = tree();
        List<Major> majors = tree.majors().stream()
                .map(m -> new Major(m.getCode(), m.getName(),
                        tabs(tree, m).stream().map(this::chip).filter(Chip::open).toList()))
                .toList();
        return new CategoriesResponse(majors, chipStats.updatedAt());
    }

    @Transactional(readOnly = true)
    public List<AdminChip> adminCategories() {
        Tree tree = tree();
        List<AdminChip> out = new ArrayList<>();
        for (Category m : tree.majors()) {
            for (Category c : tabs(tree, m)) {
                ChipStatsService.Stat s = chipStats.stat(c.getId());
                out.add(new AdminChip(c.getCode(), c.getName(), m.getCode(), c.isAggregate(), s.total(), s.analyzed(),
                        s.open(), RankingGate.failureReason(s.analyzed())));
            }
        }
        return out;
    }

    // ── GET /rankings ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RankingResponse ranking(String code, String goalParam, String sortParam, Filters filters,
                                   int page, int size, Actor actor) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        Category chip = openChip(code);
        GoalType goal = goal(goalParam);
        EerBand band = kcalBandResolver.resolve(actor);
        ListSort sort = ListSort.from(sortParam);
        Owner owner = ownerResolver.resolveOrNull(actor);

        ProductListQuery.Page hits = listQuery.page(Scope.chip(chip.getId()), true, filters, goal, EerBand.slot(goal, band),
                sort, null, size, page * size);
        List<ProductCard> cards = cardService.cards(hits.ids(), goal, band, owner, sort);
        return new RankingResponse(info(chip), goal.name(), band.kcal(), sort.name(), sort.label(), coverage(chip),
                hits.total(), page, size, (long) (page + 1) * size < hits.total(),
                ranked(cards, page * size, sort, goal, () -> scoreRankBase(chip, filters, goal, band, cards)));
    }

    // ── GET /rankings/overview ────────────────────────────────────────────────────

    /** 대분류 전체 — 열린 칩마다 필터에 맞는 상위 3개, 칩 탭 순서대로. 0개인 칩은 뺀다(열린 칩이 없으면 groups 빈 배열). */
    @Transactional(readOnly = true)
    public OverviewResponse overview(String majorCode, String goalParam, Filters filters, Actor actor) {
        Tree tree = tree();
        Category major = tree.majors().stream().filter(m -> m.getCode().equals(majorCode)).findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 대분류입니다."));
        GoalType goal = goal(goalParam);
        EerBand band = kcalBandResolver.resolve(actor);
        Owner owner = ownerResolver.resolveOrNull(actor);

        List<Group> groups = new ArrayList<>();
        for (Category c : tabs(tree, major)) {
            if (!chipStats.stat(c.getId()).open()) continue;
            ProductListQuery.Page hits = listQuery.page(Scope.chip(c.getId()), true, filters, goal, EerBand.slot(goal, band),
                    ListSort.SCORE, null, OVERVIEW_TOP, 0);
            if (hits.ids().isEmpty()) continue;
            groups.add(new Group(info(c), coverage(c), ranked(cardService.cards(hits.ids(), goal, band, owner, ListSort.SCORE), 0, ListSort.SCORE, goal, () -> 0L)));
        }
        return new OverviewResponse(major.getCode(), major.getName(), goal.name(), band.kcal(), groups);
    }

    // ── GET /rankings/leaders ─────────────────────────────────────────────────────

    /**
     * 랭킹 첫 화면 (SRCH_007 · 11-24) — 대분류 15마다 순위가 열린 중분류 가운데 앞 3개(탭 순서)의 1위 제품을 한 번에.
     * 필터가 켜져 있으면 필터에 맞는 제품이 있는 중분류만 센다. 합산 칩은 넣지 않는다(중분류만). 1위가 없는 대분류 묶음은 뺀다.
     * 서로 다른 중분류끼리 순위를 매기지 않는다 — 각 줄은 그 중분류 안의 1위다.
     */
    @Transactional(readOnly = true)
    public LeadersResponse leaders(String goalParam, Filters filters, Actor actor) {
        Tree tree = tree();
        GoalType goal = goal(goalParam);
        EerBand band = kcalBandResolver.resolve(actor);
        int slot = EerBand.slot(goal, band);
        Owner owner = ownerResolver.resolveOrNull(actor);

        record Pick(Category chip, long productId) {}
        Map<Category, List<Pick>> byMajor = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (Category major : tree.majors()) {
            List<Pick> picks = new ArrayList<>(LEADER_CHIPS);
            for (Category c : tabs(tree, major)) {
                if (c.isAggregate() || !chipStats.stat(c.getId()).open()) continue;
                List<Long> top = listQuery.top(Scope.chip(c.getId()), filters, goal, slot, 1);
                if (top.isEmpty()) continue;
                picks.add(new Pick(c, top.get(0)));
                ids.add(top.get(0));
                if (picks.size() == LEADER_CHIPS) break;
            }
            if (!picks.isEmpty()) byMajor.put(major, picks);
        }

        if (ids.isEmpty()) {
            return new LeadersResponse(goal.name(), band.kcal(), chipStats.updatedAt(), List.of());
        }
        Map<Long, ProductCard> cards = new HashMap<>();
        for (ProductCard card : cardService.cards(ids.stream().distinct().toList(), goal, band, owner, ListSort.SCORE)) {
            cards.put(card.productId(), card);
        }
        List<LeaderGroup> groups = new ArrayList<>();
        byMajor.forEach((major, picks) -> {
            List<Leader> items = picks.stream()
                    .filter(p -> cards.containsKey(p.productId()))
                    .map(p -> new Leader(info(p.chip()), cards.get(p.productId())))
                    .toList();
            if (!items.isEmpty()) groups.add(new LeaderGroup(major.getCode(), major.getName(), items));
        });
        return new LeadersResponse(goal.name(), band.kcal(), chipStats.updatedAt(), groups);
    }

    // ── GET /rankings/filters · /rankings/brands ──────────────────────────────────

    @Transactional(readOnly = true)
    public FilterSheetResponse filters(String code, Filters filters) {
        Scope scope = scope(code);
        long count = listQuery.count(scope, true, filters);

        long[] total = new long[1];
        Map<FilterChip, Long> chipCounts = listQuery.chipCounts(scope, total);
        List<NutrientChip> nutrients = new ArrayList<>();
        for (FilterChip c : FilterChip.values()) {
            long n = chipCounts.getOrDefault(c, 0L);
            boolean hidden = n < FilterChip.HIDE_BELOW_COUNT || (total[0] > 0 && (double) n / total[0] > FilterChip.HIDE_ABOVE_RATIO);
            nutrients.add(new NutrientChip(c.name(), c.label(), c.rule(), n, hidden));
        }

        List<ProductListQuery.BrandCount> brands = listQuery.brandCounts(scope);
        List<BrandItem> top = brands.stream().filter(ProductListQuery.BrandCount::confirmed).limit(TOP_BRANDS)
                .map(b -> new BrandItem(b.name(), b.count())).toList();

        List<SellerCount> sellers = new ArrayList<>();
        for (Map.Entry<Seller, Long> e : listQuery.sellerCounts(scope, true).entrySet()) {
            sellers.add(new SellerCount(e.getKey().name(), e.getKey().label(), e.getValue(), e.getKey().available()));
        }
        return new FilterSheetResponse(count, nutrients, new Brands(top, brands.size()), sellers);
    }

    @Transactional(readOnly = true)
    public BrandListResponse brands(String code, String q) {
        String needle = q == null ? "" : q.trim();
        List<BrandItem> items = listQuery.brandCounts(scope(code)).stream()
                .filter(b -> needle.isEmpty() || b.name().contains(needle))
                .sorted(Comparator.comparing(ProductListQuery.BrandCount::name))
                .map(b -> new BrandItem(b.name(), b.count()))
                .toList();
        return new BrandListResponse(items.size(), items);
    }

    // ── 요청 파라미터 ──────────────────────────────────────────────────────────────

    public static Filters filters(List<String> brands, List<String> nutrients, List<String> sellers) {
        return new Filters(
                brands == null ? List.of() : brands.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList(),
                nutrients == null ? List.of() : nutrients.stream().filter(s -> !s.isBlank()).map(FilterChip::from).distinct().toList(),
                sellers == null ? List.of() : sellers.stream().filter(s -> !s.isBlank()).map(Seller::from).distinct().toList());
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    /** 랭킹에 보이는 분류 — 숨긴 분류(「기타」)는 처음부터 뺀다. */
    private record Tree(List<Category> all) {
        Tree {
            all = all.stream().filter(c -> !c.isHidden()).toList();
        }

        List<Category> majors() {
            return all.stream()
                    .filter(c -> c.getParent() == null && c.getCode() != null && c.getCode().length() == 2)
                    .sorted(ORDER).toList();
        }

        List<Category> chipsOf(Category major) {
            return all.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId().equals(major.getId()) && c.getCode() != null)
                    .sorted(ORDER).toList();
        }

        Category byCode(String code) {
            return all.stream().filter(c -> code.equals(c.getCode())).findFirst().orElse(null);
        }
    }

    /** 칩 탭 순서 — 그 칩에 보이는 제품이 많은 순(SRCH_007), 같으면 시드 순서. */
    private List<Category> tabs(Tree tree, Category major) {
        return tree.chipsOf(major).stream()
                .sorted(Comparator.comparingLong((Category c) -> chipStats.stat(c.getId()).total()).reversed().thenComparing(ORDER))
                .toList();
    }

    private static final Comparator<Category> ORDER =
            Comparator.comparingInt(Category::getDisplayOrder).thenComparing(Category::getCode);

    private Tree tree() {
        return new Tree(categoryRepository.findAllByIsActiveTrue());
    }

    private Category openChip(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "category(칩 코드)는 필수입니다.");
        }
        Category c = tree().byCode(code.trim());
        if (c == null || c.getParent() == null) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 분류입니다.");
        }
        if (!chipStats.stat(c.getId()).open()) {
            throw new CustomException(ErrorCode.RANKING_NOT_OPEN);
        }
        return c;
    }

    /** 칩 코드면 그 칩, 대분류 코드면 대분류 전체. */
    private Scope scope(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "category(분류 코드)는 필수입니다.");
        }
        Category c = tree().byCode(code.trim());
        if (c == null) throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "없는 분류입니다.");
        return c.getParent() == null ? Scope.major(c.getId()) : Scope.chip(c.getId());
    }

    private Chip chip(Category c) {
        ChipStatsService.Stat s = chipStats.stat(c.getId());
        return new Chip(c.getCode(), c.getName(), c.isAggregate(), s.total(), s.analyzed(), s.open());
    }

    private static CategoryInfo info(Category c) {
        Category m = c.getParent();
        return new CategoryInfo(c.getCode(), c.getName(), m == null ? null : m.getCode(), m == null ? null : m.getName());
    }

    private Coverage coverage(Category c) {
        ChipStatsService.Stat s = chipStats.stat(c.getId());
        return new Coverage(s.total(), s.analyzed(), chipStats.updatedAt());
    }

    /**
     * 순위 번호 — 지금 정렬을 따른다. 「점수 높은 순」이면 보이는 점수가 같은 제품은 같은 번호, 다음 번호는 건너뛴다(1, 1, 3 · 11-27).
     * firstHigher: 이 쪽 첫 제품보다 점수가 높은 제품 수 (앞 쪽에서 이어지는 공동 순위용, 첫 쪽이면 0).
     */
    static List<Item> ranked(List<ProductCard> cards, int offset, ListSort sort, GoalType goal,
                             java.util.function.LongSupplier firstHigher) {
        List<Item> out = new ArrayList<>(cards.size());
        if (sort != ListSort.SCORE) {
            for (int i = 0; i < cards.size(); i++) out.add(new Item(offset + i + 1, cards.get(i)));
            return out;
        }
        BigDecimal prev = null;
        int rank = 0;
        for (int i = 0; i < cards.size(); i++) {
            BigDecimal score = score(cards.get(i), goal);
            if (i == 0) {
                rank = offset == 0 ? 1 : (int) firstHigher.getAsLong() + 1;
            } else if (score == null || prev == null || score.compareTo(prev) != 0) {
                rank = offset + i + 1;
            }
            prev = score;
            out.add(new Item(rank, cards.get(i)));
        }
        return out;
    }

    private long scoreRankBase(Category chip, Filters filters, GoalType goal, EerBand band, List<ProductCard> cards) {
        BigDecimal first = cards.isEmpty() ? null : score(cards.get(0), goal);
        return first == null ? 0 : listQuery.higherCount(Scope.chip(chip.getId()), filters, goal, EerBand.slot(goal, band), first);
    }

    private static BigDecimal score(ProductCard card, GoalType goal) {
        if (card.goals() == null) return null;
        return card.goals().stream().filter(g -> goal.name().equals(g.goal()))
                .map(ProductCard.GoalGrade::score).findFirst().orElse(null);
    }

    private static GoalType goal(String raw) {
        return raw == null || raw.isBlank() ? GoalType.DEFAULT : GoalType.from(raw);
    }
}
