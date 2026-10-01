package com.example.nutriuniv.domain.link.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.coupang.entity.CoupangLink;
import com.example.nutriuniv.domain.coupang.repository.CoupangLinkRepository;
import com.example.nutriuniv.domain.link.dto.PurchaseLinkResponse;
import com.example.nutriuniv.domain.link.entity.PurchaseMatchType;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 구매 링크 (기능명세서 8.1) — 이미 저장된 coupang_links 를 꺼내 쓴다. 외부 호출이 없으므로 「외부 실패·3초 초과·호출 제한」 조건은 자연히 만족한다
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
        LocalDateTime now = LocalDateTime.now();

        PurchaseMatchType type = PurchaseLinkRules.matchType(
                link == null ? null : link.getLinkStatus(),
                link == null ? null : link.getAffiliateUrl(),
                link == null ? null : link.getMatchType(),
                link == null ? null : link.getCoupangProductName(),
                product.getName());
        String url = link == null ? null : PurchaseLinkRules.url(type, link.getAffiliateUrl(), link.getLandingUrl());
        boolean priceVisible = link != null && PurchaseLinkRules.priceVisible(type, link.getProductPrice(), link.getLastSyncedAt(), now);

        return PurchaseLinkResponse.builder()
                .vendor(url == null ? null : "COUPANG")
                .url(url)
                .matchType(type.name())
                .price(priceVisible ? link.getProductPrice() : null)
                .priceCheckedAt(priceVisible ? link.getLastSyncedAt() : null)
                .isAd(url != null)
                .adNotice(url == null ? null : PurchaseLinkRules.AD_NOTICE)
                .build();
    }
}
