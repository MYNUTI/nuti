package com.example.nutriuniv.domain.link.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * GET /products/{productId}/purchase-link 출력 · 결과 화면 purchase (DATA_006 · RSLT_009).
 * <ul>
 *   <li>EXACT(같은 용량): 「쿠팡 12,900원 · 11월 16일 기준」 + per100Price(100g · 100mL당) · unitPrice(1개당) — 상품명으로 확인될 때만</li>
 *   <li>OTHER_SIZE(다른 용량): 「쿠팡 [6개 묶음] 1개당 2,150원 · 11월 16일 기준」(bundleCount · unitPrice) / 묶음 수를 못 읽으면 「쿠팡 [다른 용량]」. 판매가 없음</li>
 *   <li>NAME_SEARCH: 용량을 확인하지 못한 예전 연결 — 검색 결과 페이지, 가격 없음</li>
 *   <li>saleStatus UNKNOWN(판매 상태 확인 못 함): 가격 줄 없음, 버튼 「쿠팡에서 찾아보기」 → url 은 그 제품 이름의 쿠팡 검색 결과</li>
 * </ul>
 * 광고 고지는 adNotice.
 */
@Getter
@Builder
public class PurchaseLinkResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String vendor;                  // COUPANG (링크가 있을 때)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String url;

    private String matchType;               // EXACT | OTHER_SIZE | NAME_SEARCH | NONE

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String saleStatus;              // 링크가 있을 때 — ON_SALE | UNKNOWN(판매 상태 확인 못 함 · 가격 숨김 · 검색 결과로)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer price;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer per100Price;            // EXACT — 100g(mL)당 값. 쿠팡 상품명에 1개 용량 · 개수가 다 적혔을 때만

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String per100Unit;              // per100Price 의 단위 — g | mL (g 상품과 mL 상품은 견주지 않는다)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer bundleCount;            // OTHER_SIZE — 쿠팡 상품명에서 읽은 묶음 수

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer unitPrice;              // 1개당 값 = 판매가 ÷ 개수 (EXACT · OTHER_SIZE, 개수가 적혔을 때만)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime priceCheckedAt;   // price 나 unitPrice 가 있을 때 — 「○월 ○일 기준」

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean priceStale;             // priceCheckedAt 이 있을 때 — 조회한 지 24시간이 지났으면 true

    @JsonProperty("isAd")
    @Getter(AccessLevel.NONE)          // Lombok isAd() 가 「ad」 로 한 번 더 나가지 않게
    private boolean isAd;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String adNotice;                // 구매 버튼 옆 고지 문구 (링크가 있을 때)
}
