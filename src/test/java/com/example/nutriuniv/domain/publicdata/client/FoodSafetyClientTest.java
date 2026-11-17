package com.example.nutriuniv.domain.publicdata.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FoodSafetyClientTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void 정상_응답이면_행과_전체_수를_읽는다() {
        String body = """
                {"C005":{"total_count":"2","row":[{"BAR_CD":"8801234567890","PRDLST_NM":"사과주스"},{"BAR_CD":"8801234567891","PRDLST_NM":"배주스"}],
                         "RESULT":{"MSG":"정상처리되었습니다.","CODE":"INFO-000"}}}
                """;
        ApiPage page = FoodSafetyClient.parse("C005", body, mapper);
        assertThat(page.status()).isEqualTo(ApiPage.Status.OK);
        assertThat(page.totalCount()).isEqualTo(2L);
        assertThat(page.rows()).hasSize(2);
        assertThat(page.rows().get(0)).containsEntry("PRDLST_NM", "사과주스");
    }

    @Test
    void INFO_200은_데이터_없음() {
        String body = """
                {"C002":{"RESULT":{"MSG":"해당하는 데이터가 없습니다.","CODE":"INFO-200"}}}
                """;
        ApiPage page = FoodSafetyClient.parse("C002", body, mapper);
        assertThat(page.status()).isEqualTo(ApiPage.Status.NO_DATA);
        assertThat(page.rows()).isEmpty();
    }

    @Test
    void 결과가_서비스_이름_밖에_있어도_읽는다() {
        String body = """
                {"RESULT":{"MSG":"인증키가 유효하지 않습니다.","CODE":"INFO-100"}}
                """;
        assertThat(FoodSafetyClient.parse("C005", body, mapper).status()).isEqualTo(ApiPage.Status.KEY_REJECTED);
    }

    @Test
    void 한도_초과와_잠시_후_재시도를_구분한다() {
        assertThat(FoodSafetyClient.parse("I2570", result("I2570", "INFO-300"), mapper).status()).isEqualTo(ApiPage.Status.QUOTA_EXCEEDED);
        assertThat(FoodSafetyClient.parse("I2570", result("I2570", "ERROR-503"), mapper).status()).isEqualTo(ApiPage.Status.QUOTA_EXCEEDED);
        assertThat(FoodSafetyClient.parse("I2570", result("I2570", "ERROR-500"), mapper).status()).isEqualTo(ApiPage.Status.RETRY_LATER);
        assertThat(FoodSafetyClient.parse("I2570", result("I2570", "ERROR-336"), mapper).status()).isEqualTo(ApiPage.Status.ERROR);
    }

    @Test
    void HTML_응답은_키_거부() {
        String body = "<script>alert('인증키를 확인하세요');</script>";
        assertThat(FoodSafetyClient.parse("C005", body, mapper).status()).isEqualTo(ApiPage.Status.KEY_REJECTED);
    }

    @Test
    void 빈_응답은_잠시_후_재시도() {
        assertThat(FoodSafetyClient.parse("C005", "  ", mapper).status()).isEqualTo(ApiPage.Status.RETRY_LATER);
    }

    private static String result(String svc, String code) {
        return "{\"" + svc + "\":{\"RESULT\":{\"MSG\":\"-\",\"CODE\":\"" + code + "\"}}}";
    }
}