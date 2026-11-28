package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.domain.body.service.KcalBandResolver;
import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.service.AnalysisRequestService;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.repository.CoupangLinkRepository;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.grade.calc.GradeFormula;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Fact;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Level;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.grade.service.CalibrationService;
import com.example.nutriuniv.domain.grade.service.GradeCopyService;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.history.entity.ViewSource;
import com.example.nutriuniv.domain.history.service.ViewHistoryService;
import com.example.nutriuniv.domain.link.service.PurchaseLinkService;
import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.logging.dto.ScanEventLogRequest;
import com.example.nutriuniv.domain.logging.service.LoggingService;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.dto.ProductResultResponse;
import com.example.nutriuniv.domain.product.dto.ScanResultResponse;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductState;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import com.example.nutriuniv.domain.product.util.ServingSize;
import com.example.nutriuniv.domain.ranking.service.ChipStatsService;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.scan.entity.BarcodeName;
import com.example.nutriuniv.domain.scan.service.BarcodePickService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 결과 화면 조립 (v3 RSLT_002 · CORE_004 · SCAN_010) — 제품 상세와 바코드 스캔이 같은 결과(ProductResultResponse)를 낸다.
 * <ul>
 *   <li>두 목표를 한 번에. 감량 점수는 KcalBandResolver 의 칼로리 구간(동의 · 저장한 계정만, 그 밖은 2000)으로</li>
 *   <li>등급은 사전계산(product_grades)을 먼저 보고, 없으면(배치 전) 현행 기준으로 즉석 계산</li>
 *   <li>점수가 없으면(성분 7가지 중 빠짐) goals 는 빈 배열 + 성분값 보강 요청 자동 등록</li>
 *   <li>기준선 수치는 내보내지 않는다 — 단계(적은 편 · 보통 · 많은 편)와 1일 기준치 % 만</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductResultService {

    /** 마시는 환자용 균형영양식 결과에 넣는 한 줄 (DATA_005 · 다이어트용으로 오해하지 않게). */
    static final String PATIENT_FOOD_NOTE = "환자용 식품이에요";

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final CoupangLinkRepository coupangLinkRepository;
    private final OwnerResolver ownerResolver;
    private final GradeLookupService gradeLookupService;
    private final CalibrationService calibrationService;
    private final GradeCopyService copyService;
    private final SavedProductRepository savedProductRepository;
    private final AnalysisRequestService analysisRequestService;
    private final LoggingService loggingService;
    private final ProductListQuery listQuery;
    private final ViewHistoryService viewHistoryService;
    private final KcalBandResolver kcalBandResolver;
    private final ChipStatsService chipStats;
    private final BarcodePickService barcodePickService;
    private final TransactionTemplate txTemplate;

    public ProductResultService(ProductRepository productRepository,
                                ProductNutrientRepository productNutrientRepository,
                                CoupangLinkRepository coupangLinkRepository,
                                OwnerResolver ownerResolver,
                                GradeLookupService gradeLookupService,
                                CalibrationService calibrationService,
                                GradeCopyService copyService,
                                SavedProductRepository savedProductRepository,
                                AnalysisRequestService analysisRequestService,
                                LoggingService loggingService,
                                ProductListQuery listQuery,
                                ViewHistoryService viewHistoryService,
                                KcalBandResolver kcalBandResolver,
                                ChipStatsService chipStats,
                                BarcodePickService barcodePickService,
                                PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.productNutrientRepository = productNutrientRepository;
        this.coupangLinkRepository = coupangLinkRepository;
        this.ownerResolver = ownerResolver;
        this.gradeLookupService = gradeLookupService;
        this.calibrationService = calibrationService;
        this.copyService = copyService;
        this.savedProductRepository = savedProductRepository;
        this.analysisRequestService = analysisRequestService;
        this.loggingService = loggingService;
        this.listQuery = listQuery;
        this.viewHistoryService = viewHistoryService;
        this.kcalBandResolver = kcalBandResolver;
        this.chipStats = chipStats;
        this.barcodePickService = barcodePickService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // ── GET /products/barcode/{barcode} (SCAN_010) ────────────────────────────────

    public ScanResultResponse scan(String rawBarcode, Actor actor) {
        String barcode;
        try {
            barcode = BarcodeNormalizer.normalize(rawBarcode);                 // 형식 400, 체크섬 400
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.BARCODE_CHECKSUM_INVALID) {
                // 조회하지 않고 사실만 기록 — 클라가 보내는 /logging/scan-event 와 구분되게 surface=SERVER
                loggingService.logScanEvent(ScanEventLogRequest.serverChecksumFail(rawBarcode), LogContext.from(actor));
            }
            throw e;
        }
        EerBand band = kcalBandResolver.resolve(actor);

        ScanResultResponse res = txTemplate.execute(status -> {
            Owner owner = ownerResolver.resolveOrNull(actor);
            List<Product> found = productRepository.findByBarcode(barcode);
            List<Product> active = found.stream().filter(Product::isActive).toList();

            // 분기 순서 (SCAN_010): 분석 완료 → 여러 개 → 등급 없음 → 비슷한 후보 → 이름만 아는 바코드 → 없음
            if (active.size() == 1) return single(barcode, active.get(0), band, actor);
            if (active.size() > 1) {
                // 내가 고른 제품이 있으면 다시 묻지 않는다 (SCAN_009)
                Optional<Long> mine = barcodePickService.ownPick(barcode, owner);
                Optional<Product> picked = mine.flatMap(id -> active.stream().filter(p -> p.getId().equals(id)).findFirst());
                if (picked.isPresent()) return single(barcode, picked.get(), band, actor);

                Map<Long, Long> picks = barcodePickService.pickCounts(barcode);   // 많이 고른 제품을 위로
                List<ScanResultResponse.Candidate> candidates = active.stream()
                        .sorted(java.util.Comparator.comparingLong((Product p) -> -picks.getOrDefault(p.getId(), 0L)))
                        .map(ProductResultService::candidate).toList();
                return new ScanResultResponse("MULTIPLE", barcode, null, candidates, null);
            }

            // 우리 제품에 없는 바코드 — 확정 전에 내가 「맞아요」로 고른 제품이면 바로 결과 (SCAN_008)
            Optional<Product> mine = barcodePickService.ownPick(barcode, owner)
                    .flatMap(productRepository::findById).filter(Product::isActive);
            if (mine.isPresent()) return single(barcode, mine.get(), band, actor);

            if (found.isEmpty()) analysisRequestService.registerNewProductByBarcodeQuietly(barcode, actor);   // 비활성 제품만 있으면 등록하지 않는다
            Optional<BarcodeName> known = barcodePickService.knownName(barcode);
            if (known.isPresent()) {
                ScanResultResponse.KnownName kn = new ScanResultResponse.KnownName(known.get().getName(), known.get().getCompany());
                Optional<Product> similar = barcodePickService.similar(known.get())
                        .flatMap(sim -> productRepository.findById(sim.productId()));
                if (similar.isPresent()) {
                    return new ScanResultResponse("CANDIDATE", barcode, null, List.of(candidate(similar.get())), kn);
                }
                return new ScanResultResponse("NAME_ONLY", barcode, null, null, kn);
            }
            return new ScanResultResponse("NOT_FOUND", barcode, null, null, null);
        });
        if (res != null && res.result() != null) recordView(actor, res.result().productId(), ViewSource.BARCODE);
        return res;
    }

    // ── 공용 조립 ─────────────────────────────────────────────────────────────────

    /**
     * 호출자가 트랜잭션(또는 OSIV) 안에서 부른다 — brand · category 지연 로딩.
     * @param insufficientChannel 점수가 없을 때 자동 등록에 남길 경로 (AUTO_SCAN · AUTO_RESULT)
     */
    public ProductResultResponse build(Product product, ProductNutrient nutrient, CoupangLink link, EerBand band,
                                       Actor actor, AnalysisRequestChannel insufficientChannel) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        ProductStatus status = ProductStatus.of(nutrient);
        boolean excluded = product.isScoreExcluded();
        ProductState state = ProductState.of(excluded, status, product.isDiscontinued(), link);
        ServingSize serving = nutrient == null ? null : ServingSize.parse(nutrient.getServingSize());
        String basis = ServingSize.basisOf(serving);
        boolean scored = !excluded && status == ProductStatus.ANALYZED;
        if (!excluded && !scored) {   // 점수 제외는 분석 요청으로 보내지 않는다 (DATA_003 · 11-17)
            analysisRequestService.registerNutritionFillQuietly(product.getId(), actor, insufficientChannel);
        }

        Calibration cal = calibrationService.current();
        double transFatThreshold = threshold(cal, Nutrient.TRANS_FAT);
        Map<Nutrient, BigDecimal> per100g = nutrient == null ? Map.of() : per100g(nutrient);

        List<ProductResultResponse.GoalResult> goals = new ArrayList<>();
        Map<GoalType, Map<Nutrient, NutrientEvaluator.Tone>> tones = new EnumMap<>(GoalType.class);
        if (scored) {
            for (GoalType goal : GoalType.values()) {
                GradeLookupService.GradeView view = gradeLookupService.lookup(product.getId(), goal, band)
                        .orElseGet(() -> gradeLookupService.computeOnTheFly(nutrient, goal, band));
                NutrientEvaluator.Evaluation ev = NutrientEvaluator.evaluate(goal, subject(goal, band, nutrient, per100g), transFatThreshold);
                tones.put(goal, ev.tones());
                goals.add(new ProductResultResponse.GoalResult(
                        goal.name(), goal.label(), view.score(), view.grade(), view.label(), ev.summary(),
                        ev.goods().stream().map(ProductResultService::point).toList(),
                        ev.bads().stream().map(ProductResultService::point).toList(),
                        ev.bads().isEmpty() ? NutrientEvaluator.NO_BADS : null,
                        better(product, goal, band, view.grade()),
                        rank(product, goal, band, view.score())));
            }
        }

        return new ProductResultResponse(
                product.getId(),
                product.getName(),
                brandLine(product),
                product.getImageUrl(),
                state.name(),
                state.label(),
                product.getCategory() == null ? null
                        : new ProductResultResponse.CategoryInfo(product.getCategory().getCode(), product.getCategory().getName()),
                product.isPatientFood() ? PATIENT_FOOD_NOTE : null,
                serving == null ? null : new ProductResultResponse.Serving(serving.text(), serving.amount(), serving.unit()),
                basis,
                band.kcal(),
                scored ? cal.version() : null,
                goals,
                nutrient == null ? List.of() : nutrientRows(nutrient, per100g, transFatThreshold, tones),
                PurchaseLinkService.of(product, link, LocalDateTime.now()),
                isSaved(owner, product.getId()),
                copyService.body("SOURCE_NOTE"));
    }

    /** 제품 하나 — 결과(RESULT) 또는 등급 없음(NO_SCORE). */
    private ScanResultResponse single(String barcode, Product product, EerBand band, Actor actor) {
        ProductNutrient nutrient = productNutrientRepository.findById(product.getId()).orElse(null);
        CoupangLink link = coupangLinkRepository.findByProduct(product).orElse(null);
        ProductResultResponse r = build(product, nutrient, link, band, actor, AnalysisRequestChannel.AUTO_SCAN);
        // 점수 제외는 점수가 없어도 결과 화면으로 연다(「점수를 매기지 않는 종류예요」 + 성분표, 분석 요청으로 잇지 않음)
        boolean noScore = r.goals().isEmpty() && !ProductState.SCORE_EXCLUDED.name().equals(r.state());
        return new ScanResultResponse(noScore ? "NO_SCORE" : "RESULT", barcode, r, null, null);
    }

    private static ScanResultResponse.Candidate candidate(Product p) {
        ProductState state = ProductState.of(p.isScoreExcluded(), ProductStatus.of(p.getProductNutrient()), p.isDiscontinued(), p.getCoupangLink());
        return new ScanResultResponse.Candidate(p.getId(), p.getName(), brandLine(p), p.getImageUrl(), state.name(), state.label());
    }

    /** 브랜드 줄 — 브랜드가 비어 있으면 「제조 ○○」, 둘 다 없으면 null. */
    public static String brandLine(Product p) {
        String brand = p.getBrand() == null ? null : p.getBrand().getName();
        if (brand != null && !brand.isBlank()) return brand;
        return p.getMaker() == null || p.getMaker().isBlank() ? null : "제조 " + p.getMaker();
    }

    /** 결과 화면 기록 (RTN_001) — 실패해도 결과는 그대로 낸다. */
    public void recordView(Actor actor, Long productId, ViewSource source) {
        try {
            viewHistoryService.record(ownerResolver.resolveOrNull(actor), productId, source);
        } catch (RuntimeException e) {
            log.warn("[HISTORY] 기록 실패 productId={}: {}", productId, e.getMessage());
        }
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    /** 같은 중분류에서 더 맞는 제품 (COMP_001) — TOP(A·B) / AVAILABLE / NONE. */
    private ProductCard.BetterChoice better(Product product, GoalType goal, EerBand band, String grade) {
        long count = listQuery.betterCounts(List.of(product.getId()), goal, EerBand.slot(goal, band))
                .getOrDefault(product.getId(), 0L);
        return ProductCardService.better(grade, count);
    }

    /**
     * 종류(중분류) 안 순위 줄 — 순위가 열린 종류(분석 완료 300개 이상)이고 이 제품이 순위 대상(단종 · 현행 아님 · 숨긴 분류 아님)일 때만.
     * 칩에 보이는 제품 전체(같이 보이는 곳 포함) 가운데, 점수가 더 높은 제품 수 + 1.
     */
    private ProductResultResponse.Rank rank(Product product, GoalType goal, EerBand band, BigDecimal score) {
        Category c = product.getCategory();
        if (c == null || c.isHidden() || score == null || product.isDiscontinued() || !product.isCurrent()) return null;
        if (!chipStats.stat(c.getId()).open()) return null;
        ProductListQuery.Scope scope = ProductListQuery.Scope.chip(c.getId());
        long total = listQuery.count(scope, true, ProductListQuery.Filters.NONE);
        if (total == 0) return null;
        long rank = listQuery.higherCount(scope, ProductListQuery.Filters.NONE, goal, EerBand.slot(goal, band), score) + 1;
        return new ProductResultResponse.Rank(c.getName(), total, rank, c.getName() + " " + total + "개 중 " + rank + "위");
    }

    private boolean isSaved(Owner owner, Long productId) {
        if (owner == null) return false;
        return owner.isUser()
                ? savedProductRepository.existsByUserIdAndProductIdAndProductIsActiveTrue(owner.userId(), productId)
                : savedProductRepository.existsByAnonymousIdAndProductIdAndProductIsActiveTrue(owner.anonymousId(), productId);
    }

    private static ProductResultResponse.Point point(NutrientEvaluator.Point p) {
        return new ProductResultResponse.Point(p.nutrient().name(), p.text(), p.missing(),
                p.missing() ? NutrientEvaluator.MISSING_NOTE : null);
    }

    public static double threshold(Calibration cal, Nutrient n) {
        return cal.rules(GoalType.DEFAULT).stream()
                .filter(r -> r.nutrient() == n)
                .mapToDouble(Calibration.Rule::threshold)
                .findFirst().orElse(0.0);
    }

    /** 평가 대상 — 100g(mL)당 값, 1회 제공량당 식이섬유, 100mL 표시 여부, 근육 증가 칼로리 가점 여부 (CORE_004). */
    static NutrientEvaluator.Subject subject(GoalType goal, EerBand band, ProductNutrient n, Map<Nutrient, BigDecimal> per100g) {
        GradeFormula.Input input = GradeLookupService.toInput(n);
        boolean bonus = GradeFormula.raw(goal, EerBand.slot(goal, band), input).calorieBonus();
        return new NutrientEvaluator.Subject(per100g, n.getFiber(), input.perMl(), bonus);
    }

    public static List<ProductResultResponse.NutrientRow> nutrientRows(ProductNutrient n, Map<Nutrient, BigDecimal> per100g,
                                                                        double transFatThreshold,
                                                                        Map<GoalType, Map<Nutrient, NutrientEvaluator.Tone>> tones) {
        List<ProductResultResponse.NutrientRow> rows = new ArrayList<>();
        for (Fact f : Fact.values()) {
            BigDecimal v100 = f.graded() != null ? per100g.get(f.graded()) : per100g(n, f);
            BigDecimal vServing = serving(n, f);
            Map<String, String> toneByGoal = null;
            if (f.graded() != null && !tones.isEmpty()) {
                toneByGoal = new LinkedHashMap<>();
                for (Map.Entry<GoalType, Map<Nutrient, NutrientEvaluator.Tone>> e : tones.entrySet()) {
                    toneByGoal.put(e.getKey().name(), e.getValue().get(f.graded()).name());
                }
            }
            rows.add(new ProductResultResponse.NutrientRow(
                    f.name(), f.label(), f.unit(),
                    amount(f, v100, transFatThreshold),
                    vServing == null ? null : amount(f, vServing, transFatThreshold),
                    v100 == null,
                    toneByGoal));
        }
        return rows;
    }

    private static ProductResultResponse.Amount amount(Fact f, BigDecimal value, double transFatThreshold) {
        Level level = NutrientEvaluator.level(f, value, transFatThreshold);
        return new ProductResultResponse.Amount(value, NutrientEvaluator.dvPercent(f, value),
                level == null ? null : level.name(), level == null ? null : level.label());
    }

    public static Map<Nutrient, BigDecimal> per100g(ProductNutrient n) {
        Map<Nutrient, BigDecimal> m = new EnumMap<>(Nutrient.class);
        for (Nutrient nu : Nutrient.values()) {
            BigDecimal v = per100g(n, Fact.of(nu));
            if (v != null) m.put(nu, v);
        }
        return m;
    }

    private static BigDecimal per100g(ProductNutrient n, Fact f) {
        return switch (f) {
            case CALORIES      -> n.getCaloriesPer100g();
            case CARBOHYDRATE  -> n.getCarbohydratePer100g();
            case SUGAR         -> n.getSugarPer100g();
            case DIETARY_FIBER -> n.getFiberPer100g();
            case PROTEIN       -> n.getProteinPer100g();
            case FAT           -> n.getFatPer100g();
            case SATURATED_FAT -> n.getSaturatedFatPer100g();
            case TRANS_FAT     -> n.getTransFatPer100g();
            case CHOLESTEROL   -> n.getCholesterolPer100g();
            case SODIUM        -> n.getSodiumPer100g();
        };
    }

    private static BigDecimal serving(ProductNutrient n, Fact f) {
        return switch (f) {
            case CALORIES      -> n.getCalories();
            case CARBOHYDRATE  -> n.getCarbohydrate();
            case SUGAR         -> n.getSugar();
            case DIETARY_FIBER -> n.getFiber();
            case PROTEIN       -> n.getProtein();
            case FAT           -> n.getFat();
            case SATURATED_FAT -> n.getSaturatedFat();
            case TRANS_FAT     -> n.getTransFat();
            case CHOLESTEROL   -> n.getCholesterol();
            case SODIUM        -> n.getSodium();
        };
    }
}
