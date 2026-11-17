package com.example.nutriuniv.domain.publicdata.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DataGoKrClientTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void items가_배열이면_그대로_읽는다() {
        String body = """
                {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                 "body":{"pageNo":1,"totalCount":3,"numOfRows":100,"items":[{"FOOD_CD":"A1"},{"FOOD_CD":"A2"},{"FOOD_CD":"A3"}]}}
                """;
        ApiPage page = DataGoKrClient.parse(body, mapper);
        assertThat(page.status()).isEqualTo(ApiPage.Status.OK);
        assertThat(page.totalCount()).isEqualTo(3L);
        assertThat(page.rows()).extracting(r -> r.get("FOOD_CD")).containsExactly("A1", "A2", "A3");
    }

    @Test
    void response로_감싸고_item이_배열이어도_읽는다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"OK"},
                 "body":{"totalCount":"2","items":{"item":[{"prdlstNm":"두부"},{"prdlstNm":"우유"}]}}}}
                """;
        ApiPage page = DataGoKrClient.parse(body, mapper);
        assertThat(page.totalCount()).isEqualTo(2L);
        assertThat(page.rows()).extracting(r -> r.get("prdlstNm")).containsExactly("두부", "우유");
    }

    @Test
    void item이_하나면_객체로_와도_한_행() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"OK"},
                 "body":{"totalCount":1,"items":{"item":{"prdlstNm":"두부"}}}}}
                """;
        assertThat(DataGoKrClient.parse(body, mapper).rows()).hasSize(1);
    }

    @Test
    void 배열_안에_item으로_한_번_더_감싸도_벗긴다() {
        String body = """
                {"header":{"resultCode":"00","resultMsg":"OK"},
                 "body":{"totalCount":2,"items":[{"item":{"PRDLST_NM":"김치"}},{"item":{"PRDLST_NM":"만두"}}]}}
                """;
        assertThat(DataGoKrClient.parse(body, mapper).rows()).extracting(r -> r.get("PRDLST_NM")).containsExactly("김치", "만두");
    }

    @Test
    void 결과코드_03은_데이터_없음() {
        String body = """
                {"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{}}
                """;
        assertThat(DataGoKrClient.parse(body, mapper).status()).isEqualTo(ApiPage.Status.NO_DATA);
    }

    @Test
    void 게이트웨이_XML_오류를_읽는다() {
        String quota = """
                <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
                <returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                """;
        String key = """
                <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                """;
        assertThat(DataGoKrClient.parse(quota, mapper).status()).isEqualTo(ApiPage.Status.QUOTA_EXCEEDED);
        assertThat(DataGoKrClient.parse(key, mapper).status()).isEqualTo(ApiPage.Status.KEY_REJECTED);
    }
}