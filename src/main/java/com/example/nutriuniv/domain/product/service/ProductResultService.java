package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.entity.AnalysisRequestChannel;
import com.example.nutriuniv.domain.analysis.service.AnalysisRequestService;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.Calibration;
import com.example.nutriuniv.domain.grade.calc.TopReasonBuilder;
import com.example.nutriuniv.domain.grade.entity.Nutrient;
import com.example.nutriuniv.domain.grade.service.CalibrationService;
import com.example.nutriuniv.domain.grade.service.GradeCopyService;
import com.example.nutriuniv.domain.grade.service.GradeLookupService;
import com.example.nutriuniv.domain.logging.dto.LogContext;
import com.example.nutriuniv.domain.logging.dto.ScanEventLogRequest;
import com.example.nutriuniv.domain.logging.service.LoggingService;
import com.example.nutriuniv.domain.product.dto.BarcodeScanResponse;
import com.example.nutriuniv.domain.product.dto.GradeBadge;
import com.example.nutriuniv.domain.product.dto.NutritionFacts;
import com.example.nutriuniv.domain.product.dto.ProductSummary;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * 결과 화면 공용 조립 (기능명세서 2.3·3.2·5.1) — 제품 상세와 바코드 스캔이 같은 블록(status·product·nutrition·grade·topReason·appliedGoal·saved)을 낸다.
 * <ul>
 *   <li>상태는 영양정보로 판정(ProductStatus.of). 영양정보 부족이면 등급 없이 제품명·이미지만 + 대기 목록(NUTRITION_FILL) 자동 등록</li>
 *   <li>등급은 사전계산(product_grades)을 먼저 보고, 없으면(배치 전) 현행 기준으로 즉석 계산 — 「분석 완료인데 등급 없음」이 생기지 않게</li>
 *   <li>감점 요인 1개는 서버가 완성 문장으로(TopReasonBuilder). 목표 미설정·동의 전은 일반 기준</li>
 * </ul>
 */
@Service
public class ProductResultService {

    private final ProductRepository productRepository;
    private final ProductNutrientRepository productNutrientRepository;
    private final OwnerResolver ownerResolver;
    private final GradeLookupService gradeLookupService;
    private final CalibrationService calibrationService;
    private final GradeCopyService copyService;
    private final SavedProductRepository savedProductRepository;
    private final AnalysisRequestService analysisRequestService;
    private final LoggingService loggingService;
    private final TransactionTemplate txTemplate;

    public ProductResultService(ProductRepository productRepository,
                                ProductNutrientRepository productNutrientRepository,
                                OwnerResolver ownerResolver,
                                GradeLookupService gradeLookupService,
                                CalibrationService calibrationService,
                                GradeCopyService copyService,
                                SavedProductRepository savedProductRepository,
                                AnalysisRequestService analysisRequestService,
                                LoggingService loggingService,
                                PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.productNutrientRepository = productNutrientRepository;
        this.ownerResolver = ownerResolver;
        this.gradeLookupService = gradeLookupService;
        this.calibrationService = calibrationService;
        this.copyService = copyService;
        this.savedProductRepository = savedProductRepository;
        this.analysisRequestService = analysisRequestService;
        this.loggingService = loggingService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 조립 결과 — 응답 DTO 두 종류(상세·스캔)가 이걸 나눠 쓴다. gradeView 는 1차 웹 호환 pns 블록용. */
    public record ProductResult(
            ProductStatus status,
            ProductSummary product,
            NutritionFacts nutrition,
            GradeBadge grade,
            GradeLookupService.GradeView gradeView,
            String topReason,
            GoalType appliedGoal,
            boolean saved
    ) {}

    // ── GET /products/barcode/{barcode} (3.1·3.2) ─────────────────────────────────

    public BarcodeScanResponse scan(String rawBarcode, Actor actor) {
        String barcode;
        try {
            barcode = BarcodeNormalizer.normalize(rawBarcode);                 // 8·12·13·14 → 13, 형식 400, 체크섬 400
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.BARCODE_CHECKSUM_INVALID) {
                // 조회하지 않고 사실만 기록 (3.1) — 클라가 보내는 /logging/scan-event 와 구분되게 surface=SERVER
                loggingService.logScanEvent(ScanEventLogRequest.serverChecksumFail(rawBarcode), LogContext.from(actor));
            }
            throw e;
        }

        return txTemplate.execute(status -> {
            Product product = productRepository.findByBarcode(barcode).orElse(null);
            if (product == null) {
                // 인식은 됐는데 데이터에 없음 → 대기 목록(NEW_PRODUCT) 자동 등록(같은 바코드 횟수 +1) 후 404. 인식 실패와 다른 화면
                analysisRequestService.registerNewProductByBarcodeQuietly(barcode, actor);
                throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND, "데이터에 없는 제품이에요. 분석 대기 목록에 등록했어요.");
            }
            if (!product.isActive()) {
                throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            ProductNutrient nutrient = productNutrientRepository.findById(product.getId()).orElse(null);
            ProductResult r = build(product, nutrient, actor, AnalysisRequestChannel.AUTO_SCAN);
            return BarcodeScanResponse.builder()
                    .status(r.status().name())
                    .product(r.product())
                    .grade(r.grade())
                    .topReason(r.topReason())
                    .appliedGoal(r.appliedGoal().name())
                    .build();
        });
    }

    // ── 공용 조립 ─────────────────────────────────────────────────────────────────

    /**
     * 호출자가 트랜잭션(또는 OSIV) 안에서 부른다 — Product 의 brand 등 지연 로딩.
     * @param insufficientChannel 영양정보 부족일 때 자동 등록에 남길 경로 (AUTO_SCAN·AUTO_RESULT)
     */
    public ProductResult build(Product product, ProductNutrient nutrient, Actor actor, AnalysisRequestChannel insufficientChannel) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        GoalType goal = gradeLookupService.resolveGoalType(owner);
        boolean saved = isSaved(owner, product.getId());
        ProductStatus status = ProductStatus.of(nutrient);
        ProductSummary summary = ProductSummary.of(product, nutrient, copyService.body("SOURCE_NOTE"));
        NutritionFacts nutrition = NutritionFacts.from(nutrient);

        if (status != ProductStatus.ANALYZED) {
            analysisRequestService.registerNutritionFillQuietly(product.getId(), actor, insufficientChannel);
            return new ProductResult(status, summary, nutrition, null, null, null, goal, saved);
        }

        GradeLookupService.GradeView view = gradeLookupService.lookup(product.getId(), goal)
                .orElseGet(() -> gradeLookupService.computeOnTheFly(nutrient, goal));
        GradeBadge badge = GradeBadge.builder()
                .score(view.score())
                .grade(view.grade())
                .label(view.label())
                .badgeText(copyService.body("BADGE_PRE_CATEGORY"))     // 1·2차 「분류 비교 전」 고정
                .fiberIncluded(nutrient.getFiberPer100g() != null)
                .build();
        return new ProductResult(status, summary, nutrition, badge, view, topReason(view, nutrient, goal), goal, saved);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private boolean isSaved(Owner owner, Long productId) {
        if (owner == null) return false;
        return owner.isUser()
                ? savedProductRepository.existsByUserIdAndProductIdAndProductIsActiveTrue(owner.userId(), productId)
                : savedProductRepository.existsByAnonymousIdAndProductIdAndProductIsActiveTrue(owner.anonymousId(), productId);
    }

    private String topReason(GradeLookupService.GradeView view, ProductNutrient n, GoalType goal) {
        if (view.topPenaltyNutrient() == null) return null;
        Nutrient top = Nutrient.valueOf(view.topPenaltyNutrient());
        Double threshold = calibrationService.current().rules(goal).stream()
                .filter(r -> r.nutrient() == top)
                .map(Calibration.Rule::threshold)
                .findFirst().orElse(null);
        return TopReasonBuilder.build(top, servingValue(n, top), per100gValue(n, top), threshold);
    }

    private static BigDecimal servingValue(ProductNutrient n, Nutrient nutrient) {
        return switch (nutrient) {
            case CALORIES      -> n.getCalories();
            case PROTEIN       -> n.getProtein();
            case DIETARY_FIBER -> n.getFiber();
            case SUGAR         -> n.getSugar();
            case SATURATED_FAT -> n.getSaturatedFat();
            case TRANS_FAT     -> n.getTransFat();
            case CHOLESTEROL   -> n.getCholesterol();
            case SODIUM        -> n.getSodium();
        };
    }

    private static BigDecimal per100gValue(ProductNutrient n, Nutrient nutrient) {
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
}
