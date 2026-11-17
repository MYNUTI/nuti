package com.example.nutriuniv.domain.publicdata.compute;

import com.example.nutriuniv.domain.product.util.ContentHashCalculator;
import com.example.nutriuniv.domain.publicdata.brand.BrandNameRules;
import com.example.nutriuniv.domain.publicdata.brand.BrandResolver;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class MasterBuilderTest {

    private final List<BrandResolver.Input> brandInputs = new ArrayList<>();
    private final MasterBuilder builder = new MasterBuilder(new BrandNameRules(), in -> {
        brandInputs.add(in);
        return "브랜드-" + in.productName();
    });

    // ── 영양성분DB ──

    @Test
    void 영양성분DB_행은_가공식품_상용제품만() {
        var r = build(input().nutri(
                nutri("D1", "과자", "(주)회사", "20020614179957", "300g", "200", "446.666666"),
                with(nutri("D2", "품목", "(주)회사", null, "100g", "1", "1"), "DB_CLASS_NM", "품목대표")));
        assertThat(r.rows()).hasSize(1);
        Map<String, String> d1 = row(r, "D1");
        assertThat(d1.get("source")).isEqualTo("nutri_db");
        assertThat(d1.get("report_no")).isEqualTo("20020614179957");
        assertThat(d1.get("product_name")).isEqualTo("과자");
        assertThat(d1.get("maker")).isEqualTo("(주)회사");
        assertThat(d1.get("serving_size")).isEqualTo("300g");
        assertThat(d1.get("calories_kcal_per_100g")).isEqualTo("200");
        assertThat(d1.get("calories_kcal")).isEqualTo("600");
        assertThat(d1.get("sodium_mg_per_100g")).isEqualTo("446.67");
        assertThat(d1.get("sodium_mg")).isEqualTo("1340");
        assertThat(d1.get("sugar_g")).isNull();                       // 원천에 없으면 빈 값
        assertThat(d1.get("is_discontinued")).isEqualTo("false");
        assertThat(d1.get("content_hash")).isEqualTo(ContentHashCalculator.hash(d1));
    }

    @Test
    void 같은_food_cd가_여러_번이면_나중_것() {
        var r = build(input().nutri(
                nutri("D1", "옛 이름", "회사", null, "100g", "1", "1"),
                nutri("D1", "새 이름", "회사", null, "100g", "1", "1")));
        assertThat(r.rows()).hasSize(1);
        assertThat(row(r, "D1").get("product_name")).isEqualTo("새 이름");
    }

    // ── barcode ──

    @Test
    void 바코드는_보고번호로_우선순위_순서대로() {
        var r = build(input()
                .nutri(nutri("D1", "과자", "회사", "20020614179957", "100g", "1", "1"))
                .c005(Map.of("PRDLST_REPORT_NO", "20020614179957", "BAR_CD", "8801234567893"))
                .i0490(Map.of("PRDLST_REPORT_NO", "20020614179957", "BRCDNO", "8809665551047")));
        assertThat(row(r, "D1").get("barcode")).isEqualTo("08809665551047");   // I0490 > C005
    }

    @Test
    void 보고번호로_못_찾으면_제품명과_제조사로() {
        var r = build(input()
                .nutri(nutri("D1", "초코 과자", "(주)회사", null, "100g", "1", "1"))
                .c005(Map.of("PRDLST_REPORT_NO", "11111111111", "BAR_CD", "8801234567893", "PRDLST_NM", "초코과자", "BSSH_NM", "주식회사 회사")));
        assertThat(row(r, "D1").get("barcode")).isEqualTo("08801234567893");
        assertThat(r.stats().barcodeByName()).isEqualTo(1);
    }

    @Test
    void 한_바코드에_생산_중_2개면_둘_다_비운다() {
        var r = build(input()
                .nutri(nutri("D1", "가", "회사", "20020614179957", "100g", "1", "1"),
                        nutri("D2", "나", "회사", "20020614179958", "100g", "1", "1"))
                .c005(Map.of("PRDLST_REPORT_NO", "20020614179957", "BAR_CD", "8801234567893"),
                        Map.of("PRDLST_REPORT_NO", "20020614179958", "BAR_CD", "8801234567893")));
        assertThat(row(r, "D1").get("barcode")).isNull();
        assertThat(row(r, "D2").get("barcode")).isNull();
        assertThat(r.stats().barcodeCleared()).isEqualTo(2);
    }

    @Test
    void 생산_중_1개와_생산중단이면_생산_중_쪽만_남긴다() {
        var r = build(input()
                .nutri(nutri("D1", "가", "회사", "20020614179957", "100g", "1", "1"),
                        nutri("D2", "나", "회사", "20020614179958", "100g", "1", "1"))
                .c005(Map.of("PRDLST_REPORT_NO", "20020614179957", "BAR_CD", "8801234567893"),
                        Map.of("PRDLST_REPORT_NO", "20020614179958", "BAR_CD", "8801234567893"))
                .discontinued("20020614179958"));
        assertThat(row(r, "D1").get("barcode")).isEqualTo("08801234567893");
        assertThat(row(r, "D2").get("barcode")).isNull();
        assertThat(row(r, "D2").get("is_discontinued")).isEqualTo("true");
    }

    // ── 원재료 ──

    @Test
    void 원재료는_C002_C006_HACCP_순서_같은_출처는_CHNG_DT_최신() {
        var r = build(input()
                .nutri(nutri("D1", "가", "회사", "20020614179957", "100g", "1", "1"),
                        nutri("D2", "나", "회사", "20020614179958", "100g", "1", "1"),
                        nutri("D3", "다", "회사", "20020614179959", "100g", "1", "1"))
                .c002(Map.of("PRDLST_REPORT_NO", "20020614179957", "RAWMTRL_NM", "새 원재료", "CHNG_DT", "20260101"),
                        Map.of("PRDLST_REPORT_NO", "20020614179957", "RAWMTRL_NM", "옛 원재료", "CHNG_DT", "20250101"))
                .c006(Map.of("PRDLST_REPORT_NO", "20020614179957", "RAWMTRL_NM", "축산", "CHNG_DT", "20270101"),
                        Map.of("PRDLST_REPORT_NO", "20020614179958", "RAWMTRL_NM", "축산 원재료", "CHNG_DT", "20250101"))
                .haccp(Map.of("prdlstReportNo", "20020614179958", "rawmtrl", "HACCP 원재료"),
                        Map.of("prdlstReportNo", "20020614179959", "rawmtrl", "알수없음")));
        assertThat(row(r, "D1").get("ingredients")).isEqualTo("새 원재료");
        assertThat(row(r, "D2").get("ingredients")).isEqualTo("축산 원재료");
        assertThat(row(r, "D3").get("ingredients")).isNull();
    }

    // ── 푸드QR ──

    @Test
    void 푸드QR은_한국어_바코드당_최신_판_영양을_100g당으로() {
        var r = build(input()
                .foodQrList(
                        foodQr("1000", "8801007965697", "해물죽", "420g", "20240101", "1"),
                        foodQr("1001", "8801007965697", "해물죽", "420g", "20250101", "1"),
                        with(foodQr("1003", null, "Porridge", "420g", "20250101", "1"), "LGG_CD", "002"))
                .foodQrNutrition(
                        nutrition("1001", "19910144038912", "1", "총 내용량당", "420", "g", "열량", "245"),
                        nutrition("1001", "19910144038912", "1", "총 내용량당", "420", "g", "나트륨", "780"),
                        nutrition("1001", "19910144038912", "2", "100g당", "100", "g", "열량", "9999")));
        assertThat(r.rows()).hasSize(1);
        Map<String, String> q = row(r, "FQR-1001");
        assertThat(q.get("source")).isEqualTo("foodqr");
        assertThat(q.get("barcode")).isEqualTo("08801007965697");
        assertThat(q.get("report_no")).isEqualTo("19910144038912");
        assertThat(q.get("maker")).isEqualTo("업체");                  // 제조 표가 없으면 목록 BZENTY_NM
        assertThat(q.get("calories_kcal_per_100g")).isEqualTo("58.33");
        assertThat(q.get("calories_kcal")).isEqualTo("245");
        assertThat(q.get("sodium_mg_per_100g")).isEqualTo("185.71");
        assertThat(q.get("sodium_mg")).isEqualTo("780");
        assertThat(q.get("brand")).isNull();                        // 푸드QR 은 브랜드 규칙 밖
    }

    @Test
    void 영양성분DB와_보고번호가_겹치는_푸드QR은_뺀다() {
        var r = build(input()
                .nutri(nutri("D1", "과자", "회사", "20020614179957", "100g", "1", "1"))
                .foodQrList(foodQr("2000", null, "과자", "100g", "20250101", "1"))
                .foodQrMaker(Map.of("ELB_MNG_NO", "2000", "IMRPT_NO", "20020614179957", "BUES_NM", "제조사")));
        assertThat(r.rows()).hasSize(1);
        assertThat(r.stats().foodQrExcluded()).isEqualTo(1);
    }

    // ── brand · category ──

    @Test
    void 브랜드는_묶음_대표_행으로_묶음_전체에_같은_값() {
        var r = build(input().nutri(
                nutri("D2", "우유", "회사", null, "200mL", "1", "1"),
                nutri("D1", "우유", "회사", null, "200mL", "1", "1")));
        assertThat(row(r, "D1").get("brand")).isEqualTo("브랜드-우유");
        assertThat(row(r, "D2").get("brand")).isEqualTo("브랜드-우유");
        assertThat(brandInputs).hasSize(1);
        assertThat(r.grouping().assigned()).hasSize(2);
        assertThat(r.stats().newGroups()).isEqualTo(1);
    }

    @Test
    void 바코드_주인은_판매원이_붙은_880_번호로_정한다() {
        var r = build(input()
                .nutri(with(nutri("D1", "가", "제조사A", "20020614179957", "100g", "1", "1"), "SELLER_MANUFAC_NM", "(주)판매회사"),
                        nutri("D2", "나", "제조사B", "20020614179958", "100g", "1", "1"))
                .c005(Map.of("PRDLST_REPORT_NO", "20020614179957", "BAR_CD", "8801234567893"),
                        Map.of("PRDLST_REPORT_NO", "20020614179958", "BAR_CD", "8801234560009")));
        BrandResolver.Input d2 = brandInputs.stream().filter(i -> "나".equals(i.productName())).findFirst().orElseThrow();
        assertThat(d2.barcodeOwner()).isEqualTo("판매회사");            // 앞 9자리 880123456 의 주인
    }

    @Test
    void 분류는_지금_값을_이어_쓰고_새_제품은_검토_목록() {
        var r = build(input()
                .nutri(nutri("D1", "가", "회사", null, "100g", "1", "1"), nutri("D2", "나", "회사", null, "100g", "1", "1"))
                .categories(Map.of("D1", "0505")));
        assertThat(row(r, "D1").get("category")).isEqualTo("0505");
        assertThat(row(r, "D2").get("category")).isNull();
        assertThat(r.newWithoutCategory()).containsExactly("D2");
        assertThat(r.stats().newProducts()).isEqualTo(1);
    }

    // ── 도우미 ──

    private MasterBuilder.Result build(In in) {
        return builder.build(in.toInput());
    }

    private static Map<String, String> row(MasterBuilder.Result r, String id) {
        return r.rows().stream().filter(m -> id.equals(m.get("master_id"))).findFirst().orElseThrow();
    }

    private static Map<String, String> nutri(String id, String name, String maker, String report, String weight, String kcal, String sodium) {
        Map<String, String> m = new HashMap<>();
        m.put("FOOD_CD", id);
        m.put("FOOD_NM_KR", name);
        m.put("DB_GRP_NM", "가공식품");
        m.put("DB_CLASS_NM", "상용제품");
        m.put("MAKER_NM", maker);
        if (report != null) m.put("ITEM_REPORT_NO", report);
        m.put("Z10500", weight);
        m.put("AMT_NUM1", kcal);
        m.put("AMT_NUM13", sodium);
        return m;
    }

    private static Map<String, String> foodQr(String elb, String barcode, String name, String ctv, String ymd, String ver) {
        Map<String, String> m = new HashMap<>();
        m.put("ELB_MNG_NO", elb);
        m.put("LGG_CD", "001");
        if (barcode != null) m.put("BRCD_NO", barcode);
        m.put("PRDCT_NM", name);
        m.put("CTV", ctv);
        m.put("VLD_BGNG_YMD", ymd);
        m.put("VER_NO", ver);
        m.put("BZENTY_NM", "업체");
        return m;
    }

    private static Map<String, String> nutrition(String elb, String report, String sn, String cd, String tct, String tcd, String name, String cta) {
        return Map.of("ELB_MNG_NO", elb, "IMRPT_NO", report, "NTRTN_INDCT_SN", sn, "NTRTN_INDCT_CD", cd,
                "NTRTN_INDCT_TCT", tct, "NTRTN_INDCT_TCD", tcd, "NIRWMT_NM", name, "CTA", cta);
    }

    private static Map<String, String> with(Map<String, String> m, String k, String v) {
        Map<String, String> c = new HashMap<>(m);
        c.put(k, v);
        return c;
    }

    private static In input() {
        return new In();
    }

    /** 입력 만들기 — 안 넣은 것은 빈 목록. */
    private static final class In {
        List<Map<String, String>> nutri = List.of(), list = List.of(), nutrition = List.of(), maker = List.of(),
                haccp = List.of(), c005 = List.of(), i2570 = List.of(), c002 = List.of(), c006 = List.of(), i0490 = List.of();
        Set<String> discontinued = Set.of();
        Map<String, String> categories = Map.of();

        In nutri(Map<String, String>... r) { nutri = List.of(r); return this; }
        In foodQrList(Map<String, String>... r) { list = List.of(r); return this; }
        In foodQrNutrition(Map<String, String>... r) { nutrition = List.of(r); return this; }
        In foodQrMaker(Map<String, String>... r) { maker = List.of(r); return this; }
        In haccp(Map<String, String>... r) { haccp = List.of(r); return this; }
        In c005(Map<String, String>... r) { c005 = List.of(r); return this; }
        In c002(Map<String, String>... r) { c002 = List.of(r); return this; }
        In c006(Map<String, String>... r) { c006 = List.of(r); return this; }
        In i0490(Map<String, String>... r) { i0490 = List.of(r); return this; }
        In discontinued(String... rn) { discontinued = Set.of(rn); return this; }
        In categories(Map<String, String> c) { categories = c; return this; }

        MasterInput toInput() {
            return new MasterInput(nutri, list, nutrition, maker, haccp, c005, i2570, c002, c006, i0490, discontinued, categories, Map.of());
        }
    }
}