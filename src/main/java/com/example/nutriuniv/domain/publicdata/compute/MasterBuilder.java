package com.example.nutriuniv.domain.publicdata.compute;

import com.example.nutriuniv.domain.product.util.ContentHashCalculator;
import com.example.nutriuniv.domain.publicdata.brand.BrandNameRules;
import com.example.nutriuniv.domain.publicdata.brand.BrandResolver;
import com.example.nutriuniv.domain.publicdata.compute.ComputeRules.Amount;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.example.nutriuniv.domain.publicdata.compute.ComputeRules.*;

/**
 * 원본 표 → 34컬럼 (sync.md 「컬럼별 채우는 곳」). 같은 원본이면 같은 결과가 나온다(순서 · 동점은 정렬로 고정). 순수 계산 — MasterBuilderTest 로 고정.
 * <pre>
 * 1. 행 고르기     영양성분DB(가공식품 · 상용제품) + 푸드QR(한국어 · 바코드당 최신 판, 영양성분DB 와 보고번호 · 바코드가 겹치면 뺌)
 * 2. 칸 채우기     report_no · 제품명 · 제조사 · 총내용량 · 영양성분 · 원재료 · 생산중단
 * 3. barcode      푸드QR > HACCP > I0490 > C005 > I2570 (보고번호), 없으면 제품명 + 제조사. 한 바코드가 여러 행에 걸리면 생산 중 1개일 때만 남김
 * 4. brand        영양성분DB 행만 — 묶음 대표 행 + 묶음 바코드 주인으로 BrandResolver
 * 5. category     지금 product 의 값을 이어 씀(새 제품은 null)
 * 6. content_hash
 * </pre>
 */
public final class MasterBuilder {

    public static final String SOURCE_NUTRI_DB = "nutri_db";
    public static final String SOURCE_FOODQR = "foodqr";
    static final String FOODQR_PREFIX = "FQR-";
    static final String KOREAN = "001";
    static final String HACCP_UNKNOWN = "알수없음";
    /** 바코드 주인에서 뺄 이름 — 숫자 · 특별시 · 광역시 · www. (brand.md barcodeOwner) */
    private static final Pattern BAD_OWNER = Pattern.compile("\\d|특별시|광역시|www\\.");

    public record Stats(int nutriRows, int foodQrRows, int foodQrExcluded, int withBarcode, int barcodeByName,
                        int barcodeCleared, int withIngredients, int discontinued, int withBrand,
                        int withCategory, int newProducts, int newGroups) {}

    /**
     * @param rows               34컬럼에서 시각 2칸을 뺀 32칸(master_id ~ content_hash). 값은 hash 와 같은 표기, 빈 값은 null
     * @param grouping           묶음 구성 — 새로 정한 묶음(assigned)은 저장해야 한다
     * @param newWithoutCategory 분류가 없는 새 제품 master_id(검토 목록용)
     */
    public record Result(List<Map<String, String>> rows, BrandGrouping.Result grouping, Stats stats, List<String> newWithoutCategory) {}

    private final BrandNameRules rules;
    private final Function<BrandResolver.Input, String> brandFn;
    private final Map<String, String> cleanCache = new HashMap<>();

    /**
     * @param rules   회사 이름 정리(clean) — 바코드 주인 · 제품명+제조사 바코드 찾기에 쓴다. 지명 목록이 들어간 것
     * @param brandFn 묶음 하나의 brand (BrandResolver::resolve)
     */
    public MasterBuilder(BrandNameRules rules, Function<BrandResolver.Input, String> brandFn) {
        this.rules = rules;
        this.brandFn = brandFn;
    }

    /** 계산 중인 행. */
    static final class Row {
        String masterId, source, reportNo, name, maker, ingredients, category, brand, barcode;
        Amount serving;
        final Double[] per100 = new Double[NUTRIENTS];
        boolean discontinued;
        Map<String, String> nutri;        // 영양성분DB 원본(브랜드용), 푸드QR 은 null
        String ownBarcode;                // 푸드QR 목록 자기 바코드
    }

    public Result build(MasterInput in) {
        cleanCache.clear();

        // ── 1·2. 영양성분DB 행 ──
        Map<String, Map<String, String>> nutriById = new LinkedHashMap<>();
        for (Map<String, String> r : in.nutriDb()) {
            String id = text(r.get("FOOD_CD"));
            if (id == null) continue;
            if (!"가공식품".equals(text(r.get("DB_GRP_NM"))) || !"상용제품".equals(text(r.get("DB_CLASS_NM")))) continue;
            nutriById.put(id, r);
        }
        List<Row> nutriRows = new ArrayList<>(nutriById.size());
        for (Map.Entry<String, Map<String, String>> e : nutriById.entrySet()) {
            Map<String, String> r = e.getValue();
            Row row = new Row();
            row.masterId = e.getKey();
            row.source = SOURCE_NUTRI_DB;
            row.reportNo = reportNo(r.get("ITEM_REPORT_NO"));
            row.name = text(r.get("FOOD_NM_KR"));
            row.maker = text(r.get("MAKER_NM"));
            row.serving = amount(r.get("Z10500"));
            for (int i = 0; i < NUTRIENTS; i++) row.per100[i] = number(r.get(NUTRI_DB_FIELDS[i]));
            row.nutri = r;
            nutriRows.add(row);
        }

        // ── 3. 바코드 찾기 표 ──
        List<Map<String, TreeSet<String>>> byReport = List.of(
                barcodesByReport(concat(in.foodQrNutrition(), in.foodQrMaker()), "IMRPT_NO", "BRCD_NO"),
                barcodesByReport(in.haccp(), "prdlstReportNo", "barcode"),
                barcodesByReport(in.i0490(), "PRDLST_REPORT_NO", "BRCDNO"),
                barcodesByReport(in.c005(), "PRDLST_REPORT_NO", "BAR_CD"),
                barcodesByReport(in.i2570(), "PRDLST_REPORT_NO", "BRCD_NO"));
        List<Map<String, TreeSet<String>>> byName = List.of(
                barcodesByName(in.haccp(), "prdlstNm", "manufacture", "barcode"),
                barcodesByName(in.c005(), "PRDLST_NM", "BSSH_NM", "BAR_CD"),
                barcodesByName(in.i2570(), "PRDT_NM", "CMPNY_NM", "BRCD_NO"));
        int[] barcodeByName = {0};

        Set<String> nutriReports = new HashSet<>();
        Set<String> nutriBarcodes = new HashSet<>();
        for (Row row : nutriRows) {
            row.barcode = findBarcode(row, byReport, byName, barcodeByName);
            if (row.reportNo != null) nutriReports.add(row.reportNo);
            if (row.barcode != null) nutriBarcodes.add(row.barcode);
        }

        // ── 1·2. 푸드QR 행 ──
        int[] excluded = {0};
        List<Row> foodQrRows = foodQrRows(in, byReport, byName, barcodeByName, nutriReports, nutriBarcodes, excluded);

        List<Row> all = new ArrayList<>(nutriRows.size() + foodQrRows.size());
        all.addAll(nutriRows);
        all.addAll(foodQrRows);

        // ── 원재료 · 생산중단 ──
        Map<String, String> c002 = latestIngredients(in.c002());
        Map<String, String> c006 = latestIngredients(in.c006());
        Map<String, String> haccp = new HashMap<>();
        for (Map<String, String> r : in.haccp()) {
            String rn = reportNo(r.get("prdlstReportNo"));
            String raw = text(r.get("rawmtrl"));
            if (rn != null && raw != null && !HACCP_UNKNOWN.equals(raw)) haccp.put(rn, raw);
        }
        for (Row row : all) {
            if (row.reportNo == null) continue;
            String ing = c002.get(row.reportNo);
            if (ing == null) ing = c006.get(row.reportNo);
            if (ing == null) ing = haccp.get(row.reportNo);
            row.ingredients = ing;
            row.discontinued = in.discontinued().contains(row.reportNo);
        }

        // ── 3. 한 바코드가 여러 행에 걸리면 ──
        int cleared = resolveBarcodeConflicts(all);

        // ── 4. brand ──
        BrandGrouping.Result grouping = assignBrands(nutriRows, in);

        // ── 5·6. 분류 · 출력 ──
        List<Map<String, String>> out = new ArrayList<>(all.size());
        List<String> newWithoutCategory = new ArrayList<>();
        int withBarcode = 0, withIngredients = 0, discontinued = 0, withBrand = 0, withCategory = 0, newProducts = 0;
        for (Row row : all) {
            boolean known = in.categories().containsKey(row.masterId);
            row.category = text(in.categories().get(row.masterId));
            if (!known) {
                newProducts++;
                if (row.category == null) newWithoutCategory.add(row.masterId);
            }
            out.add(toMap(row));
            if (row.barcode != null) withBarcode++;
            if (row.ingredients != null) withIngredients++;
            if (row.discontinued) discontinued++;
            if (row.brand != null) withBrand++;
            if (row.category != null) withCategory++;
        }
        Collections.sort(newWithoutCategory);
        int newGroups = (int) grouping.assigned().stream().filter(BrandGrouping.Assigned::representative).count();
        Stats stats = new Stats(nutriRows.size(), foodQrRows.size(), excluded[0], withBarcode, barcodeByName[0], cleared,
                withIngredients, discontinued, withBrand, withCategory, newProducts, newGroups);
        return new Result(out, grouping, stats, newWithoutCategory);
    }

    // ── 푸드QR ───────────────────────────────────────────────────────────────

    private List<Row> foodQrRows(MasterInput in, List<Map<String, TreeSet<String>>> byReport, List<Map<String, TreeSet<String>>> byName,
                                 int[] barcodeByName, Set<String> nutriReports, Set<String> nutriBarcodes, int[] excluded) {
        // 한국어 판만, 라벨관리번호당 마지막 줄
        Map<String, Map<String, String>> byElb = new LinkedHashMap<>();
        for (Map<String, String> r : in.foodQrList()) {
            String elb = text(r.get("ELB_MNG_NO"));
            if (elb == null || !KOREAN.equals(text(r.get("LGG_CD")))) continue;
            byElb.put(elb, r);
        }
        // 바코드당 최신 판 1개 (VLD_BGNG_YMD, VER_NO 최신). 바코드 없는 판은 모두 남긴다
        Map<String, Map<String, String>> latestByBarcode = new HashMap<>();
        for (Map<String, String> r : byElb.values()) {
            String bc = text(r.get("BRCD_NO"));
            if (bc == null) continue;
            latestByBarcode.merge(bc, r, (a, b) -> newer(b, a) ? b : a);
        }
        Map<String, List<Map<String, String>>> nutrition = new HashMap<>();
        for (Map<String, String> r : in.foodQrNutrition()) {
            String elb = text(r.get("ELB_MNG_NO"));
            if (elb != null) nutrition.computeIfAbsent(elb, k -> new ArrayList<>()).add(r);
        }
        Map<String, Map<String, String>> makers = new HashMap<>();
        for (Map<String, String> r : in.foodQrMaker()) {
            String elb = text(r.get("ELB_MNG_NO"));
            if (elb != null) makers.put(elb, r);
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> e : byElb.entrySet()) {
            Map<String, String> r = e.getValue();
            String bc = text(r.get("BRCD_NO"));
            if (bc != null && latestByBarcode.get(bc) != r) continue;

            List<Map<String, String>> nut = firstDisplayGroup(nutrition.getOrDefault(e.getKey(), List.of()));
            Map<String, String> mk = makers.get(e.getKey());
            Row row = new Row();
            row.masterId = FOODQR_PREFIX + e.getKey();
            row.source = SOURCE_FOODQR;
            for (Map<String, String> n : nut) {
                row.reportNo = reportNo(n.get("IMRPT_NO"));
                if (row.reportNo != null) break;
            }
            if (row.reportNo == null && mk != null) row.reportNo = reportNo(mk.get("IMRPT_NO"));
            row.name = text(r.get("PRDCT_NM"));
            row.maker = mk == null ? null : text(mk.get("BUES_NM"));
            if (row.maker == null) row.maker = text(r.get("BZENTY_NM"));
            row.serving = amount(r.get("CTV"));
            if (row.serving == null && !nut.isEmpty()) row.serving = amount(nut.get(0).get("NTRTN_INDCT_TCT"), nut.get(0).get("NTRTN_INDCT_TCD"));
            fillFoodQrNutrients(row, nut);
            row.ownBarcode = gtin14(bc);
            row.barcode = findBarcode(row, byReport, byName, barcodeByName);

            // 영양성분DB 와 보고번호 · 바코드가 겹치면 영양성분DB 쪽을 쓴다
            if ((row.reportNo != null && nutriReports.contains(row.reportNo))
                    || (row.barcode != null && nutriBarcodes.contains(row.barcode))) {
                excluded[0]++;
                continue;
            }
            rows.add(row);
        }
        return rows;
    }

    /** a 가 b 보다 새 판인가 — VLD_BGNG_YMD, 같으면 VER_NO. */
    private static boolean newer(Map<String, String> a, Map<String, String> b) {
        int c = digits(a.get("VLD_BGNG_YMD")).compareTo(digits(b.get("VLD_BGNG_YMD")));
        if (c != 0) return c > 0;
        Double va = number(a.get("VER_NO")), vb = number(b.get("VER_NO"));
        return (va == null ? -1 : va) > (vb == null ? -1 : vb);
    }

    /** 영양 표시 묶음이 여럿이면 NTRTN_INDCT_SN 가장 작은 것. */
    private static List<Map<String, String>> firstDisplayGroup(List<Map<String, String>> rows) {
        if (rows.isEmpty()) return rows;
        double min = Double.MAX_VALUE;
        for (Map<String, String> r : rows) {
            Double sn = number(r.get("NTRTN_INDCT_SN"));
            if (sn != null && sn < min) min = sn;
        }
        if (min == Double.MAX_VALUE) return rows;
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> r : rows) {
            Double sn = number(r.get("NTRTN_INDCT_SN"));
            if (sn != null && sn == min) out.add(r);
        }
        return out;
    }

    private static void fillFoodQrNutrients(Row row, List<Map<String, String>> nut) {
        for (Map<String, String> n : nut) {
            int idx = foodQrNutrientIndex(n.get("NIRWMT_NM"));
            Double v = number(n.get("CTA"));
            if (idx < 0 || v == null || row.per100[idx] != null) continue;
            Amount basisAmount = amount(n.get("NTRTN_INDCT_TCT"), n.get("NTRTN_INDCT_TCD"));
            Double basisNumber = basisAmount != null ? basisAmount.value() : number(n.get("NTRTN_INDCT_TCT"));
            Basis basis = basis(n.get("NTRTN_INDCT_CD"), basisNumber);
            Double amt = switch (basis) {
                case PER_100 -> null;
                case TOTAL -> basisNumber != null ? basisNumber : (row.serving == null ? null : row.serving.value());
                case UNIT -> {
                    Amount unit = amount(n.get("NTRTN_ICUT_CTV"), n.get("NTRTN_ICUT_CVUCD"));
                    Double u = unit != null ? unit.value() : number(n.get("NTRTN_ICUT_CTV"));
                    yield u != null ? u : basisNumber;
                }
            };
            row.per100[idx] = per100(v, basis, amt);
        }
    }

    // ── 바코드 ───────────────────────────────────────────────────────────────

    private String findBarcode(Row row, List<Map<String, TreeSet<String>>> byReport, List<Map<String, TreeSet<String>>> byName, int[] byNameCount) {
        if (row.ownBarcode != null) return row.ownBarcode;
        if (row.reportNo != null) {
            for (Map<String, TreeSet<String>> m : byReport) {
                TreeSet<String> s = m.get(row.reportNo);
                if (s != null && !s.isEmpty()) return s.first();
            }
        }
        String key = nameKey(row.name, row.maker);
        if (key != null) {
            for (Map<String, TreeSet<String>> m : byName) {
                TreeSet<String> s = m.get(key);
                if (s != null && !s.isEmpty()) {
                    byNameCount[0]++;
                    return s.first();
                }
            }
        }
        return null;
    }

    private static Map<String, TreeSet<String>> barcodesByReport(List<Map<String, String>> rows, String reportField, String barcodeField) {
        Map<String, TreeSet<String>> m = new HashMap<>();
        for (Map<String, String> r : rows) {
            String rn = reportNo(r.get(reportField));
            String g = gtin14(r.get(barcodeField));
            if (rn != null && g != null) m.computeIfAbsent(rn, k -> new TreeSet<>()).add(g);
        }
        return m;
    }

    private Map<String, TreeSet<String>> barcodesByName(List<Map<String, String>> rows, String nameField, String makerField, String barcodeField) {
        Map<String, TreeSet<String>> m = new HashMap<>();
        for (Map<String, String> r : rows) {
            String g = gtin14(r.get(barcodeField));
            if (g == null) continue;
            String key = nameKey(r.get(nameField), r.get(makerField));
            if (key != null) m.computeIfAbsent(key, k -> new TreeSet<>()).add(g);
        }
        return m;
    }

    /** 제품명 + 제조사 일치 키 — compact(제품명) | compact(clean(제조사)). 둘 중 하나라도 비면 null. */
    String nameKey(String name, String maker) {
        String n = BrandNameRules.compact(text(name));
        String m = text(maker);
        if (n.isEmpty() || m == null) return null;
        String c = BrandNameRules.compact(clean(m));
        return c.isEmpty() ? null : n + "|" + c;
    }

    /**
     * 한 바코드가 여러 행에 걸리면: 생산 중 2개 이상 → 모두 비움, 생산 중 1개 → 그 행만 남김, 생산중단끼리 → 모두 비움.
     * @return 비운 행 수
     */
    static int resolveBarcodeConflicts(List<Row> rows) {
        Map<String, List<Row>> byBarcode = new HashMap<>();
        for (Row r : rows) if (r.barcode != null) byBarcode.computeIfAbsent(r.barcode, k -> new ArrayList<>()).add(r);
        int cleared = 0;
        for (List<Row> holders : byBarcode.values()) {
            if (holders.size() < 2) continue;
            List<Row> active = holders.stream().filter(r -> !r.discontinued).toList();
            for (Row r : holders) {
                if (active.size() == 1 && active.get(0) == r) continue;
                r.barcode = null;
                cleared++;
            }
        }
        return cleared;
    }

    // ── 원재료 ───────────────────────────────────────────────────────────────

    /** 보고번호 → 원재료. 같은 보고번호가 여럿이면 CHNG_DT 최신(같으면 나중에 받은 것). */
    private static Map<String, String> latestIngredients(List<Map<String, String>> rows) {
        Map<String, String> text = new HashMap<>();
        Map<String, String> date = new HashMap<>();
        for (Map<String, String> r : rows) {
            String rn = reportNo(r.get("PRDLST_REPORT_NO"));
            String raw = text(r.get("RAWMTRL_NM"));
            if (rn == null || raw == null) continue;
            String d = digits(r.get("CHNG_DT"));
            String prev = date.get(rn);
            if (prev == null || d.compareTo(prev) >= 0) {
                date.put(rn, d);
                text.put(rn, raw);
            }
        }
        return text;
    }

    // ── brand ────────────────────────────────────────────────────────────────

    private BrandGrouping.Result assignBrands(List<Row> nutriRows, MasterInput in) {
        // 880 바코드 앞 9자리 (보고번호별, C005)
        Map<String, Set<String>> prefixesByReport = new HashMap<>();
        for (Map<String, String> r : in.c005()) {
            String rn = reportNo(r.get("PRDLST_REPORT_NO"));
            String p = prefix880(r.get("BAR_CD"));
            if (rn != null && p != null) prefixesByReport.computeIfAbsent(rn, k -> new TreeSet<>()).add(p);
        }
        // 880 번호 주인표 (brand.md 조회표 4) — 판매원이 있는 행만, 앞 9자리별 판매원 다수결
        Map<String, Map<String, Integer>> votes = new HashMap<>();
        for (Row row : nutriRows) {
            String seller = text(row.nutri.get("SELLER_MANUFAC_NM"));
            if (seller == null || row.reportNo == null) continue;
            String owner = clean(seller);
            for (String p : prefixesByReport.getOrDefault(row.reportNo, Set.of())) {
                votes.computeIfAbsent(p, k -> new HashMap<>()).merge(owner, 1, Integer::sum);
            }
        }
        Map<String, String> ownerOfPrefix = new HashMap<>();
        votes.forEach((p, v) -> ownerOfPrefix.put(p, mostCommon(v)));

        // 묶음
        List<BrandGrouping.Member> members = new ArrayList<>(nutriRows.size());
        Map<String, Row> byId = new HashMap<>();
        for (Row row : nutriRows) {
            members.add(new BrandGrouping.Member(row.masterId, row.nutri.get("FOOD_NM_KR"), row.nutri.get("MAKER_NM"), row.nutri.get("Z10500")));
            byId.put(row.masterId, row);
        }
        BrandGrouping.Result grouping = BrandGrouping.assign(members, in.storedGroups());

        Map<String, List<Row>> rowsOfGroup = new HashMap<>();
        for (Row row : nutriRows) rowsOfGroup.computeIfAbsent(grouping.groupOf().get(row.masterId), k -> new ArrayList<>()).add(row);

        for (Map.Entry<String, List<Row>> e : rowsOfGroup.entrySet()) {
            String g = e.getKey();
            Row rep = byId.get(grouping.representativeOf().get(g));
            Map<String, Integer> owners = new HashMap<>();
            Set<String> seenPrefix = new HashSet<>();
            for (Row row : e.getValue()) {
                if (row.reportNo == null) continue;
                for (String p : prefixesByReport.getOrDefault(row.reportNo, Set.of())) {
                    if (!seenPrefix.add(p)) continue;
                    String o = ownerOfPrefix.get(p);
                    if (o != null && !BAD_OWNER.matcher(o).find()) owners.merge(o, 1, Integer::sum);
                }
            }
            Map<String, String> r = rep.nutri;
            String brand = brandFn.apply(new BrandResolver.Input(g, r.get("FOOD_NM_KR"), r.get("SELLER_MANUFAC_NM"),
                    r.get("MAKER_NM"), r.get("IMP_MANUFAC_NM"), owners.isEmpty() ? null : mostCommon(owners)));
            for (Row row : e.getValue()) row.brand = text(brand);
        }
        return grouping;
    }

    /** 880 으로 시작하는 바코드(13자리 기준)의 앞 9자리. 아니면 null. */
    static String prefix880(String raw) {
        String d = digits(raw);
        if (d.length() == 14 && d.charAt(0) == '0') d = d.substring(1);
        return d.length() >= 9 && d.startsWith("880") ? d.substring(0, 9) : null;
    }

    /** 가장 많은 것. 같으면 이름 순으로 앞. */
    private static String mostCommon(Map<String, Integer> counts) {
        String best = null;
        int n = -1;
        for (Map.Entry<String, Integer> e : new TreeMap<>(counts).entrySet()) {
            if (e.getValue() > n) {
                best = e.getKey();
                n = e.getValue();
            }
        }
        return best;
    }

    private String clean(String raw) {
        return cleanCache.computeIfAbsent(raw, k -> {
            String c = rules.clean(k);
            return c == null || c.isBlank() ? k.strip() : c;
        });
    }

    // ── 출력 ─────────────────────────────────────────────────────────────────

    private static Map<String, String> toMap(Row row) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("master_id", row.masterId);
        m.put("barcode", row.barcode);
        m.put("report_no", row.reportNo);
        m.put("source", row.source);
        m.put("product_name", row.name);
        m.put("brand", row.brand);
        m.put("category", row.category);
        m.put("maker", row.maker);
        m.put("ingredients", row.ingredients);
        m.put("serving_size", row.serving == null ? null : row.serving.format());
        List<String> cols = ContentHashCalculator.NUTRIENT_COLUMNS;
        for (int i = 0; i < NUTRIENTS; i++) m.put(cols.get(i), formatNumber(whole(row.per100[i], row.serving)));
        for (int i = 0; i < NUTRIENTS; i++) m.put(cols.get(i) + "_per_100g", formatNumber(row.per100[i]));
        m.put("is_discontinued", ContentHashCalculator.formatBoolean(row.discontinued));
        m.put("content_hash", ContentHashCalculator.hash(m));
        return m;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }
}