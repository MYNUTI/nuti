package com.example.nutriuniv.domain.publicdata.compute;

import com.example.nutriuniv.domain.publicdata.compute.ComputeRules.Basis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeRulesTest {

    @Test
    void 보고번호는_첫_번째_숫자_11_16자리만() {
        assertThat(ComputeRules.reportNo("20020614179957")).isEqualTo("20020614179957");
        assertThat(ComputeRules.reportNo(" 2002-0614 179957 / 19870190051644")).isEqualTo("20020614179957");
        assertThat(ComputeRules.reportNo("1234567890")).isNull();             // 10자리
        assertThat(ComputeRules.reportNo("12345678901234567")).isNull();      // 17자리
        assertThat(ComputeRules.reportNo("IMP-2024-0001")).isNull();          // 수입식품 내부 코드
        assertThat(ComputeRules.reportNo("")).isNull();
    }

    @Test
    void 바코드는_체크숫자_맞는_GTIN만_14자리로() {
        assertThat(ComputeRules.gtin14("8809665551047")).isEqualTo("08809665551047");
        assertThat(ComputeRules.gtin14("08801007965697")).isEqualTo("08801007965697");
        assertThat(ComputeRules.gtin14("8809665551048")).isNull();            // 체크숫자 틀림
        assertThat(ComputeRules.gtin14("880966555104")).isNull();             // 11자리 · 체크 틀림
        assertThat(ComputeRules.gtin14("abc")).isNull();
    }

    @Test
    void 매장코드와_묶음_인디케이터는_뺀다() {
        assertThat(ComputeRules.gtin14("2000000000008")).isNull();            // EAN-13 20~29
        assertThat(ComputeRules.gtin14("200000000004")).isNull();             // UPC-12 2
        assertThat(ComputeRules.gtin14("400000000008")).isNull();             // UPC-12 4
        assertThat(ComputeRules.gtin14("18809665551044")).isNull();           // 묶음 인디케이터 1
    }

    @Test
    void 총내용량은_g_mL로_맞춘다() {
        assertThat(ComputeRules.amount("80.00g").format()).isEqualTo("80g");
        assertThat(ComputeRules.amount("2.45kg").format()).isEqualTo("2450g");
        assertThat(ComputeRules.amount("900mL").format()).isEqualTo("900mL");
        assertThat(ComputeRules.amount("1.8 L").format()).isEqualTo("1800mL");
        assertThat(ComputeRules.amount("9.5 g").format()).isEqualTo("9.5g");
        assertThat(ComputeRules.amount("300", "g").format()).isEqualTo("300g");
    }

    @Test
    void 묶음_표기나_다른_모양은_버린다() {
        assertThat(ComputeRules.amount("20g×10")).isNull();
        assertThat(ComputeRules.amount("20g x 10")).isNull();
        assertThat(ComputeRules.amount("1봉")).isNull();
        assertThat(ComputeRules.amount("0g")).isNull();
        assertThat(ComputeRules.amount(null)).isNull();
    }

    @Test
    void 푸드QR_영양_항목_이름() {
        assertThat(ComputeRules.foodQrNutrientIndex("열량")).isEqualTo(0);
        assertThat(ComputeRules.foodQrNutrientIndex("나트륨(mg)")).isEqualTo(1);
        assertThat(ComputeRules.foodQrNutrientIndex("당류")).isEqualTo(3);
        assertThat(ComputeRules.foodQrNutrientIndex("지방")).isEqualTo(4);
        assertThat(ComputeRules.foodQrNutrientIndex("트랜스 지방")).isEqualTo(5);
        assertThat(ComputeRules.foodQrNutrientIndex("포화지방")).isEqualTo(6);
        assertThat(ComputeRules.foodQrNutrientIndex("단백질")).isEqualTo(9);
        assertThat(ComputeRules.foodQrNutrientIndex("칼슘")).isEqualTo(-1);
    }

    @Test
    void 표시_기준에_따라_100g당으로() {
        // 사천짜볶이: 총 내용량(300g)당 600kcal → 100g당 200, 한 봉지 600
        Basis total = ComputeRules.basis("총 내용량당", 300.0);
        assertThat(total).isEqualTo(Basis.TOTAL);
        Double kcal = ComputeRules.per100(600, total, 300.0);
        assertThat(ComputeRules.formatNumber(kcal)).isEqualTo("200");
        assertThat(ComputeRules.formatNumber(ComputeRules.per100(1340, total, 300.0))).isEqualTo("446.67");
        assertThat(ComputeRules.formatNumber(ComputeRules.whole(ComputeRules.per100(1340, total, 300.0), ComputeRules.amount("300g")))).isEqualTo("1340");

        // 맥심 커피믹스: 단위 내용량(12g)당 50kcal → 100g당 416.67, 1180g 이면 4916.67
        Basis unit = ComputeRules.basis("단위 내용량당", 12.0);
        Double coffee = ComputeRules.per100(50, unit, 12.0);
        assertThat(ComputeRules.formatNumber(coffee)).isEqualTo("416.67");
        assertThat(ComputeRules.formatNumber(ComputeRules.whole(coffee, ComputeRules.amount("1180g")))).isEqualTo("4916.67");

        assertThat(ComputeRules.basis("100g당", 100.0)).isEqualTo(Basis.PER_100);
        assertThat(ComputeRules.basis("01", 100.0)).isEqualTo(Basis.PER_100);   // 글자로 모르면 기준량 100 → 100g당
        assertThat(ComputeRules.basis("02", 420.0)).isEqualTo(Basis.TOTAL);
    }

    @Test
    void 지명은_시군구를_뗀다() {
        assertThat(ComputeRules.placeName("포천시")).isEqualTo("포천");
        assertThat(ComputeRules.placeName("서천군")).isEqualTo("서천");
        assertThat(ComputeRules.placeName("강남구")).isEqualTo("강남");
        assertThat(ComputeRules.placeName("중구")).isNull();                   // 떼면 1글자
        assertThat(ComputeRules.placeName("123")).isNull();
    }
}