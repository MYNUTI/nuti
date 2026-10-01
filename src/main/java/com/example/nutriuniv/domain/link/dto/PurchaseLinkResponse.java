package com.example.nutriuniv.domain.link.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * GET /products/{productId}/purchase-link 출력 (기능명세서 8.1).
 * matchType 이 화면 문구를 정한다 — EXACT 「구매하기」, NAME_SEARCH 「이 제품을 검색해 보기」, NONE 링크 없음.
 * price 는 EXACT 이고 24시간 이내 조회분일 때만. 광고 표시는 구매 버튼 바로 옆에(adNotice).
 */
@Getter
@Builder
public class PurchaseLinkResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String vendor;                  // COUPANG (링크가 있을 때)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String url;

    private String matchType;               // EXACT | NAME_SEARCH | NONE

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer price;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime priceCheckedAt;   // price 가 있을 때만 — 「쿠팡 기준 · N시간 전」

    @JsonProperty("isAd")
    private boolean isAd;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String adNotice;                // 구매 버튼 옆 고지 문구 (링크가 있을 때)
}
