package com.example.nutriuniv.domain.publicdata.client;

/** 공공 API 한 페이지 호출. 제공처(식품안전나라 · 공공데이터포털)마다 하나. 네트워크 오류도 예외 대신 RETRY_LATER 로 돌려준다. */
public interface PublicApiClient {

    PublicApi.Provider provider();

    /**
     * @param page           1부터
     * @param conditionValue CHNG_DT(yyyyMMdd) · EVL_YR(yyyy) 값. 전량이면 null
     */
    ApiPage fetch(PublicApi api, int page, String conditionValue);
}