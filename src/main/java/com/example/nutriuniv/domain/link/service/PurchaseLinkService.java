package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.repository.CoupangLinkRepository;
import com.example.nutriuniv.domain.link.dto.PurchaseLinkResponse;
import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import com.example.nutriuniv.domain.link.util.PackSize;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 구매 링크 (DATA_006 · RSLT_009) — 이미 저장된 coupang_links 를 꺼내 쓴다. 외부 호출이 없으므로 「외부 실패·3초 초과·호출 제한」 조건은 자연히 만족한다
 * (직전에 받아둔 결과가 곧 이 표). 가격 비교·최저가·가격 정렬은 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PurchaseLinkService {

    private final ProductRepository productRepository;
    private final CoupangLinkRepository coupangLinkRepository;

    @Transactional(readOnly = true)
    public PurchaseLinkResponse get(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        CoupangLink link = coupangLinkRepository.findByProduct(product).orElse(null);
        return of(product, link, LocalDateTime.now());
    }

    /** 결과 화면에 싣는 구매 줄 — 호출자가 이미 읽은 링크로 만든다(추가 조회 없음). */
    public static PurchaseLinkResponse of(Product product, CoupangLink link, LocalDateTime now) {
        PurchaseMatchType type = matchType(product, link);
        if (type == PurchaseMatchType.NONE) {
            return PurchaseLinkResponse.builder().matchType(type.name()).isAd(false).build();
        }
        boolean saleUnknown = link.saleUnknown();
        String url = PurchaseLinkRules.url(type, link.getAffiliateUrl(), link.getLandingUrl(), saleUnknown);
        Integer price = link.getProductPrice();
        PackSize size = PackSize.parse(link.getCoupangProductName());

        Integer shownPrice = null, per100 = null, bundle = null, unit = null;
        String per100Unit = null;
        if (!saleUnknown && type == PurchaseMatchType.EXACT && price != null) {
            shownPrice = price;
            per100 = PurchaseLinkRules.per100(size, price);
            per100Unit = per100 == null ? null : size.unit().label();
            unit = size.complete() ? size.perUnit(price) : null;
        } else if (!saleUnknown && type == PurchaseMatchType.OTHER_SIZE && price != null && size.count() != null) {
            bundle = size.count();
            unit = size.perUnit(price);
        }
        LocalDateTime checkedAt = shownPrice != null || unit != null
                ? PurchaseLinkRules.priceCheckedAt(link.getPriceCheckedAt(), link.getLastSyncedAt()) : null;

        return PurchaseLinkResponse.builder()
                .vendor(url == null ? null : "COUPANG")
                .url(url)
                .matchType(type.name())
                .saleStatus(saleUnknown ? "UNKNOWN" : "ON_SALE")
                .price(shownPrice)
                .per100Price(per100)
                .per100Unit(per100Unit)
                .bundleCount(bundle)
                .unitPrice(unit)
                .priceCheckedAt(checkedAt)
                .priceStale(checkedAt == null ? null : !PurchaseLinkRules.priceFresh(checkedAt, now))
                .isAd(url != null)
                .adNotice(url == null ? null : PurchaseLinkRules.AD_NOTICE)
                .build();
    }

    public static PurchaseMatchType matchType(Product product, CoupangLink link) {
        return link == null ? PurchaseMatchType.NONE : PurchaseLinkRules.matchType(
                link.getLinkStatus(), link.getAffiliateUrl(), link.getMatchType(), link.getCoupangProductName(), product.getName());
    }

    /** 목록 등에서 판매가를 보여도 되는가 — 결과 화면 가격 줄과 같은 조건(같은 용량 · 판매 상태 확인됨). */
    public static boolean priceVisible(Product product, CoupangLink link) {
        return link != null && PurchaseLinkRules.priceVisible(matchType(product, link), link.getProductPrice(), link.saleUnknown());
    }
}
