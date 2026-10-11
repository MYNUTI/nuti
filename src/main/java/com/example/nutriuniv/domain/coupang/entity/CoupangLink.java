package com.example.nutriuniv.domain.coupang.entity;

import com.example.nutriuniv.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupang_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CoupangLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "coupang_product_id", length = 100)
    private String coupangProductId;

    @Column(name = "affiliate_url", length = 1000)
    private String affiliateUrl;

    @Column(name = "landing_url", length = 1000)
    private String landingUrl;

    @Column(name = "search_keyword", nullable = false, length = 255)
    private String searchKeyword;

    @Column(name = "coupang_product_name", length = 255)
    private String coupangProductName;

    @Column(name = "coupang_image_url", length = 500)
    private String coupangImageUrl;

    @Column(name = "product_price")
    private Integer productPrice;

    @Column(name = "is_rocket")
    private Boolean isRocket;

    @Column(name = "is_free_shipping")
    private Boolean isFreeShipping;

    @Column(name = "link_status", nullable = false, length = 10)
    private String linkStatus = "UNLINKED";

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    // 구매 링크 매칭 종류 수동 지정 (기능명세서 8.1) — EXACT | NAME_SEARCH. null 이면 PurchaseLinkRules 가 상품명 비교로 자동 판정.
    // 관리자가 「정확히 같은 제품」임을 확인한 경우에만 EXACT 를 넣는다(가격 노출 조건). 자동 판정은 보수적이라 대부분 NAME_SEARCH.
    @Column(name = "match_type", length = 20)
    private String matchType;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CoupangLink createDefault(Product product) {
        CoupangLink link = new CoupangLink();
        link.product = product;
        link.searchKeyword = product.getName();
        link.linkStatus = "UNLINKED";
        return link;
    }

    public void syncSuccess(String coupangProductId, String coupangProductName,
                            String affiliateUrl, String landingUrl, String coupangImageUrl,
                            Integer productPrice, Boolean isRocket, Boolean isFreeShipping) {
        this.coupangProductId = coupangProductId;
        this.coupangProductName = coupangProductName;
        this.affiliateUrl = affiliateUrl;
        this.landingUrl = landingUrl;
        this.coupangImageUrl = coupangImageUrl;
        this.productPrice = productPrice;
        this.isRocket = isRocket;
        this.isFreeShipping = isFreeShipping;
        this.linkStatus = "LINKED";
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void syncFailed() {
        this.linkStatus = "FAILED";
        this.lastSyncedAt = LocalDateTime.now();
    }

    /** 매칭 종류 수동 지정 — null 이면 자동 판정으로 되돌린다. */
    public void overrideMatchType(String matchType) {
        this.matchType = matchType;
    }
}
