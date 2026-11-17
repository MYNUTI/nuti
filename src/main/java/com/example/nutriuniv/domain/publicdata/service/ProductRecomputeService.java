package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.publicdata.brand.BrandLookupTables;
import com.example.nutriuniv.domain.publicdata.brand.BrandNameRules;
import com.example.nutriuniv.domain.publicdata.brand.BrandResolver;
import com.example.nutriuniv.domain.publicdata.brand.BrandResolvers;
import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import com.example.nutriuniv.domain.publicdata.compute.ComputeRules;
import com.example.nutriuniv.domain.publicdata.compute.MasterBuilder;
import com.example.nutriuniv.domain.publicdata.compute.MasterInput;
import com.example.nutriuniv.domain.publicdata.dto.RecomputeResponse;
import com.example.nutriuniv.domain.publicdata.entity.PublicApiRound;
import com.example.nutriuniv.domain.publicdata.repository.BrandGroupRepository;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository;
import com.example.nutriuniv.domain.publicdata.repository.PublicApiRawReader;
import com.example.nutriuniv.domain.publicdata.repository.PublicApiRoundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 34컬럼 재계산 (공공데이터 최신화 2단계) — 원본 표 전부로 34컬럼을 다시 계산해 복제 테이블(product_staging)을 채운다.
 * 실제 product 반영은 따로(ProductSyncApplyService — 미리보기 → 반영).
 * <ul>
 *   <li>완료 확인: 34컬럼에 쓰는 API 11개가 모두 전량을 한 번 이상 끝까지 받았고, 마지막 회차가 받는 중 · 실패가 아니어야 돈다.
 *       일부만 받은 채 계산하면 barcode · ingredients 가 대량으로 비거나 is_discontinued 가 대량으로 false 가 된다</li>
 *   <li>I0300 · I1420(지명 목록)은 없어도 돈다 — 시도 17개만으로 지명을 뗀다</li>
 *   <li>메모리: 큰 API(C002 · C006 · I2852)는 우리 제품의 보고번호만 남기며 흘려 읽는다</li>
 *   <li>복제 테이블 채우기와 새 묶음 저장은 한 트랜잭션 — 중간에 실패하면 둘 다 그대로</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductRecomputeService {

    /** 34컬럼에 꼭 필요한 API — 하나라도 완료가 아니면 계산하지 않는다. */
    static final List<PublicApi> REQUIRED = List.of(
            PublicApi.NUTRI_DB, PublicApi.FOODQR_LIST, PublicApi.FOODQR_NUTRITION, PublicApi.FOODQR_MAKER, PublicApi.HACCP,
            PublicApi.C005, PublicApi.I2570, PublicApi.C002, PublicApi.C006, PublicApi.I2852, PublicApi.I0490);
    static final List<PublicApi> PLACE_APIS = List.of(PublicApi.I0300, PublicApi.I1420);
    static final int SAMPLE_LIMIT = 20;

    // 원본에서 뽑는 필드 (sync.md 「출력」)
    static final List<String> NUTRI_FIELDS;
    static {
        List<String> f = new ArrayList<>(List.of("FOOD_CD", "FOOD_NM_KR", "DB_GRP_NM", "DB_CLASS_NM", "MAKER_NM",
                "SELLER_MANUFAC_NM", "IMP_MANUFAC_NM", "ITEM_REPORT_NO", "Z10500"));
        f.addAll(List.of(ComputeRules.NUTRI_DB_FIELDS));
        NUTRI_FIELDS = List.copyOf(f);
    }
    static final List<String> FOODQR_LIST_FIELDS = List.of("ELB_MNG_NO", "LGG_CD", "BRCD_NO", "PRDCT_NM", "CTV", "VLD_BGNG_YMD", "VER_NO", "BZENTY_NM");
    static final List<String> FOODQR_NUTRITION_FIELDS = List.of("ELB_MNG_NO", "IMRPT_NO", "BRCD_NO", "NTRTN_INDCT_SN", "NTRTN_INDCT_CD",
            "NTRTN_INDCT_TCT", "NTRTN_INDCT_TCD", "NTRTN_ICUT_CTV", "NTRTN_ICUT_CVUCD", "NIRWMT_NM", "CTA");
    static final List<String> FOODQR_MAKER_FIELDS = List.of("ELB_MNG_NO", "IMRPT_NO", "BRCD_NO", "BUES_NM");
    static final List<String> HACCP_FIELDS = List.of("prdlstReportNo", "barcode", "rawmtrl", "prdlstNm", "manufacture");
    static final List<String> C005_FIELDS = List.of("PRDLST_REPORT_NO", "BAR_CD", "PRDLST_NM", "BSSH_NM");
    static final List<String> I2570_FIELDS = List.of("PRDLST_REPORT_NO", "BRCD_NO", "PRDT_NM", "CMPNY_NM");
    static final List<String> INGREDIENT_FIELDS = List.of("PRDLST_REPORT_NO", "RAWMTRL_NM", "CHNG_DT");
    static final List<String> I2852_FIELDS = List.of("PRDLST_REPORT_NO");
    static final List<String> I0490_FIELDS = List.of("PRDLST_REPORT_NO", "BRCDNO");

    private final PublicApiRoundRepository roundRepository;
    private final PublicApiRawReader rawReader;
    private final BrandGroupRepository brandGroupRepository;
    private final BrandLookupTables brandTables;
    private final ProductStagingRepository stagingRepository;
    private final ProductLoadService loadService;
    private final TransactionTemplate txTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProductRecomputeService(PublicApiRoundRepository roundRepository, PublicApiRawReader rawReader,
                                   BrandGroupRepository brandGroupRepository, BrandLookupTables brandTables,
                                   ProductStagingRepository stagingRepository, ProductLoadService loadService,
                                   PlatformTransactionManager transactionManager) {
        this.roundRepository = roundRepository;
        this.rawReader = rawReader;
        this.brandGroupRepository = brandGroupRepository;
        this.brandTables = brandTables;
        this.stagingRepository = stagingRepository;
        this.loadService = loadService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean isRunning() {
        return running.get();
    }

    /** 계산을 막는 이유들 — 비어 있으면 계산할 수 있다. */
    public List<String> blockers() {
        List<String> out = new ArrayList<>();
        if (!brandTables.isReady()) out.add("브랜드 조회표가 준비되지 않았습니다: " + brandTables.problem());
        for (PublicApi api : REQUIRED) {
            Optional<PublicApiRound> latest = roundRepository.findTopByApiOrderByIdDesc(api.name());
            if (latest.isEmpty()) {
                out.add(api + " 을 받은 적이 없습니다");
                continue;
            }
            PublicApiRound r = latest.get();
            if (r.getStatus() == PublicApiRound.Status.RUNNING) out.add(api + " 을 받는 중입니다(roundId=" + r.getId() + ")");
            else if (r.getStatus() == PublicApiRound.Status.FAILED) out.add(api + " 마지막 회차가 실패했습니다(roundId=" + r.getId() + ", " + r.getLastCode() + ")");
            if (roundRepository.findTopByApiAndModeAndStatusOrderByIdDesc(api.name(), PublicApiRound.Mode.FULL, PublicApiRound.Status.DONE).isEmpty()) {
                out.add(api + " 은 전량을 끝까지 받은 적이 없습니다");
            }
        }
        return out;
    }

    public RecomputeResponse recompute() {
        List<String> blockers = blockers();
        if (!blockers.isEmpty()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "재계산할 수 없습니다 — " + String.join(" / ", blockers));
        }
        if (loadService.isLoading()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "제품 적재·반영이 진행 중입니다. 끝난 뒤 다시 하세요.");
        }
        if (!running.compareAndSet(false, true)) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "재계산이 이미 돌고 있습니다.");
        }
        try {
            return doRecompute();
        } finally {
            running.set(false);
        }
    }

    private RecomputeResponse doRecompute() {
        long started = System.currentTimeMillis();
        log.info("[RECOMPUTE] 시작");

        // 1) 행의 뼈대 — 영양성분DB · 푸드QR
        List<Map<String, String>> nutri = read(PublicApi.NUTRI_DB, NUTRI_FIELDS);
        List<Map<String, String>> foodQrList = read(PublicApi.FOODQR_LIST, FOODQR_LIST_FIELDS);
        List<Map<String, String>> foodQrNutrition = read(PublicApi.FOODQR_NUTRITION, FOODQR_NUTRITION_FIELDS);
        List<Map<String, String>> foodQrMaker = read(PublicApi.FOODQR_MAKER, FOODQR_MAKER_FIELDS);

        // 우리 제품의 보고번호 — 큰 API 는 이것만 남긴다
        Set<String> reports = new HashSet<>();
        for (Map<String, String> r : nutri) addReport(reports, r.get("ITEM_REPORT_NO"));
        for (Map<String, String> r : foodQrNutrition) addReport(reports, r.get("IMRPT_NO"));
        for (Map<String, String> r : foodQrMaker) addReport(reports, r.get("IMRPT_NO"));

        // 2) 바코드 · 원재료 · 생산중단
        List<Map<String, String>> haccp = read(PublicApi.HACCP, HACCP_FIELDS);
        List<Map<String, String>> c005 = read(PublicApi.C005, C005_FIELDS);
        List<Map<String, String>> i2570 = read(PublicApi.I2570, I2570_FIELDS);
        List<Map<String, String>> i0490 = read(PublicApi.I0490, I0490_FIELDS);
        List<Map<String, String>> c002 = latestIngredients(PublicApi.C002, reports);
        List<Map<String, String>> c006 = latestIngredients(PublicApi.C006, reports);
        Set<String> discontinued = new HashSet<>();
        rawReader.forEach(PublicApi.I2852, I2852_FIELDS, r -> {
            String rn = ComputeRules.reportNo(r.get("PRDLST_REPORT_NO"));
            if (rn != null && reports.contains(rn)) discontinued.add(rn);
        });
        log.info("[RECOMPUTE] 원본 읽음 — 영양성분DB {} · 푸드QR {}/{}/{} · HACCP {} · C005 {} · I2570 {} · I0490 {} · C002 {} · C006 {} · 생산중단 {}",
                nutri.size(), foodQrList.size(), foodQrNutrition.size(), foodQrMaker.size(), haccp.size(), c005.size(),
                i2570.size(), i0490.size(), c002.size(), c006.size(), discontinued.size());

        // 3) brand 준비 — 지명 목록 · 묶음 구성표
        Set<String> places = new HashSet<>();
        for (String w : rawReader.distinctSecondAddressWords(PLACE_APIS, "SITE_ADDR")) {
            String p = ComputeRules.placeName(w);
            if (p != null) places.add(p);
        }
        BrandNameRules rules = BrandResolvers.rules(places);
        BrandResolver resolver = BrandResolvers.withRules(brandTables, rules);

        MasterInput input = new MasterInput(nutri, foodQrList, foodQrNutrition, foodQrMaker, haccp, c005, i2570,
                c002, c006, i0490, discontinued, rawReader.currentCategories(), brandGroupRepository.findAll());

        // 4) 계산
        MasterBuilder.Result result = new MasterBuilder(rules, resolver::resolve).build(input);

        // 5) 복제 테이블 · 새 묶음 저장
        LocalDateTime now = LocalDateTime.now();
        txTemplate.executeWithoutResult(s -> {
            stagingRepository.truncate();
            stagingRepository.insertAll(result.rows(), now);
            brandGroupRepository.insertAll(result.grouping().assigned(), now);
        });

        MasterBuilder.Stats st = result.stats();
        long elapsed = System.currentTimeMillis() - started;
        log.info("[RECOMPUTE] 끝 — 복제 테이블 {}행 (영양성분DB {} · 푸드QR {}, 겹쳐 뺀 푸드QR {}) · 바코드 {} (이름으로 {} · 겹쳐 비움 {}) · 원재료 {} · 생산중단 {} · 브랜드 {} · 분류 {} · 새 제품 {} (분류 없음 {}) · 새 묶음 {} · {}ms",
                result.rows().size(), st.nutriRows(), st.foodQrRows(), st.foodQrExcluded(), st.withBarcode(), st.barcodeByName(),
                st.barcodeCleared(), st.withIngredients(), st.discontinued(), st.withBrand(), st.withCategory(), st.newProducts(),
                result.newWithoutCategory().size(), st.newGroups(), elapsed);

        return new RecomputeResponse(result.rows().size(), st.nutriRows(), st.foodQrRows(), st.foodQrExcluded(),
                st.withBarcode(), st.barcodeByName(), st.barcodeCleared(), st.withIngredients(), st.discontinued(),
                st.withBrand(), st.withCategory(), st.newProducts(), st.newGroups(),
                result.newWithoutCategory().size(),
                result.newWithoutCategory().subList(0, Math.min(SAMPLE_LIMIT, result.newWithoutCategory().size())),
                rules.placeNames().size(), elapsed, now);
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    /** 값이 있는 칸만 담은 작은 맵으로 읽는다(30만 행이라 HashMap 은 무겁다). */
    private List<Map<String, String>> read(PublicApi api, List<String> fields) {
        List<Map<String, String>> out = new ArrayList<>();
        rawReader.forEach(api, fields, compacting(out::add));
        return out;
    }

    /** 원재료 — 우리 보고번호만, 보고번호마다 CHNG_DT 최신 한 줄(같으면 나중에 받은 것). */
    private List<Map<String, String>> latestIngredients(PublicApi api, Set<String> reports) {
        Map<String, Map<String, String>> latest = new HashMap<>();
        rawReader.forEach(api, INGREDIENT_FIELDS, compacting(r -> {
            String rn = ComputeRules.reportNo(r.get("PRDLST_REPORT_NO"));
            if (rn == null || !reports.contains(rn) || ComputeRules.text(r.get("RAWMTRL_NM")) == null) return;
            Map<String, String> prev = latest.get(rn);
            if (prev == null || ComputeRules.digits(r.get("CHNG_DT")).compareTo(ComputeRules.digits(prev.get("CHNG_DT"))) >= 0) {
                latest.put(rn, r);
            }
        }));
        return new ArrayList<>(latest.values());
    }

    private static Consumer<Map<String, String>> compacting(Consumer<Map<String, String>> next) {
        return r -> {
            Map<String, String> m = new HashMap<>();
            r.forEach((k, v) -> { if (v != null && !v.isBlank()) m.put(k, v); });
            next.accept(Map.copyOf(m));
        };
    }

    private static void addReport(Set<String> reports, String raw) {
        String rn = ComputeRules.reportNo(raw);
        if (rn != null) reports.add(rn);
    }
}