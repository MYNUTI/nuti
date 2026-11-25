package com.example.nutriuniv.domain.coupang.entity;

import com.example.nutriuniv.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 제품 ↔ 쿠팡 상품 연결 (DATA_006).
 * <ul>
 *   <li>link_status: LINKED 연결 · UNLINKED 아직 안 찾음 · FAILED 찾아봤는데 없음 · REVIEW 후보는 찾았는데 용량을 확인 못 해 운영자 확인 대기(사용자에게는 안 보임)</li>
 *   <li>missed_refreshes: 가격을 새로 받을 때 연결한 상품이 검색되지 않은 연속 횟수. 2번 이상이면 「판매 상태 확인 못 함」 —
 *       가격을 숨기고 버튼은 쿠팡 검색 결과로(쿠팡 응답에 품절 칸이 없음 · 11-24). 다시 검색되면 0</li>
 * </ul>
 */
@Entity
@Table(name = "coupang_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CoupangLink {

    public static final String LINKED = "LINKED";
    public static final String UNLINKED = "UNLINKED";
    public static final String FAILED = "FAILED";
    public static final String REVIEW = "REVIEW";
    /** 이 횟수만큼 이어서 검색되지 않으면 판매 상태를 확인 못 한 것으로 본다. */
    public static final int SALE_UNKNOWN_AFTER = 2;

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
    private String linkStatus = UNLINKED;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    // 판매가를 마지막으로 받은 시각 (v3 RSLT_009) — 조회가 실패해도(syncFailed) 바뀌지 않는다. 화면은 「직전 판매가 · 조회일」
    @Column(name = "price_checked_at")
    private LocalDateTime priceCheckedAt;

    // 매칭 종류 운영자 지정 — EXACT(같은 용량) | OTHER_SIZE(다른 용량) | NAME_SEARCH. null 이면 PurchaseLinkRules 가 상품명의 용량 · 개수로 판정.
    // 확인 대기(REVIEW) 후보를 운영자가 같은 용량 / 다른 용량으로 확인하면 여기에 남는다
    @Column(name = "match_type", length = 20)
    private String matchType;

    @Column(name = "missed_refreshes", nullable = false, columnDefinition = "integer default 0")
    private int missedRefreshes = 0;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CoupangLink createDefault(Product product) {
        CoupangLink link = new CoupangLink();
        link.product = product;
        link.searchKeyword = product.getName();
        link.linkStatus = UNLINKED;
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
        this.linkStatus = LINKED;
        this.lastSyncedAt = LocalDateTime.now();
        this.missedRefreshes = 0;
        this.matchType = null;   // 새로 고른 상품은 다시 자동 판정
        if (productPrice != null) this.priceCheckedAt = this.lastSyncedAt;
    }

    /** 이 상품을 찾은 검색어 — 가격을 새로 받을 때 같은 검색어로 찾는다(줄인 검색어로 찾은 상품이 원래 이름으로는 안 나올 수 있다). */
    public void useKeyword(String keyword) {
        if (keyword != null && !keyword.isBlank()) this.searchKeyword = keyword;
    }

    /** 후보는 찾았는데 한쪽 상품명에 용량이나 개수가 없어 자동으로 잇지 않음 — 후보를 남기고 운영자 확인을 기다린다. */
    public void syncReview(String coupangProductId, String coupangProductName,
                           String affiliateUrl, String landingUrl, String coupangImageUrl,
                           Integer productPrice, Boolean isRocket, Boolean isFreeShipping) {
        syncSuccess(coupangProductId, coupangProductName, affiliateUrl, landingUrl, coupangImageUrl,
                productPrice, isRocket, isFreeShipping);
        this.linkStatus = REVIEW;
    }

    public void syncFailed() {
        this.linkStatus = FAILED;
        this.lastSyncedAt = LocalDateTime.now();
    }

    /** 운영자 확인 — 같은 용량(EXACT) · 다른 용량(OTHER_SIZE)이면 연결, 아니면 찾지 못함으로. */
    public void resolveReview(String decidedMatchType) {
        if (decidedMatchType == null) {
            this.linkStatus = FAILED;
        } else {
            this.linkStatus = LINKED;
            this.matchType = decidedMatchType;
        }
        this.lastSyncedAt = LocalDateTime.now();
    }

    /** 가격 새로 받기 — 연결한 상품이 검색됨. 판매가 · 링크 · 사진을 새 값으로. */
    public void refreshFound(String coupangProductName, String affiliateUrl, String coupangImageUrl,
                             Integer productPrice, Boolean isRocket, Boolean isFreeShipping) {
        this.coupangProductName = coupangProductName;
        this.affiliateUrl = affiliateUrl;
        this.coupangImageUrl = coupangImageUrl;
        this.productPrice = productPrice;
        this.isRocket = isRocket;
        this.isFreeShipping = isFreeShipping;
        this.missedRefreshes = 0;
        this.lastSyncedAt = LocalDateTime.now();
        if (productPrice != null) this.priceCheckedAt = this.lastSyncedAt;
    }

    /** 가격 새로 받기 — 검색은 됐는데 연결한 상품이 없음. 직전 판매가는 그대로 둔다. */
    public void refreshMissed() {
        this.missedRefreshes++;
        this.lastSyncedAt = LocalDateTime.now();
    }

    /** 판매 상태를 확인 못 함 — 가격을 숨기고 버튼은 쿠팡 검색 결과로. */
    public boolean saleUnknown() {
        return missedRefreshes >= SALE_UNKNOWN_AFTER;
    }

    /** 매칭 종류 수동 지정 — null 이면 자동 판정으로 되돌린다. */
    public void overrideMatchType(String matchType) {
        this.matchType = matchType;
    }
}
