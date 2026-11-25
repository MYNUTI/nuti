package com.example.nutriuniv.domain.product.enums;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 판매처 7곳 (v3 SRCH_008 · SRCH_006) — 필터 탭과 홈 「판매처별로 보기」가 같은 목록 · 순서.
 * 지금 붙일 수 있는 자료는 쿠팡 상품 연결뿐이다. 편의점 · 올리브영 · 컬리는 판매처 자료가 들어오면 sql 을 바꾼다(그 전까지 0개).
 */
public enum Seller {
    COUPANG("쿠팡", true),
    CU("CU", false),
    GS25("GS25", false),
    SEVEN_ELEVEN("세븐일레븐", false),
    EMART24("이마트24", false),
    OLIVE_YOUNG("올리브영", false),
    KURLY("컬리", false);

    private final String label;
    private final boolean available;

    Seller(String label, boolean available) {
        this.label = label;
        this.available = available;
    }

    public String label() { return label; }

    /** 판매처 자료가 붙어 있는가. false 면 거르면 0개. */
    public boolean available() { return available; }

    /** 쿠팡에서 지금 살 수 있는 연결 — 판매 상태를 확인 못 한 연결은 뺀다(품절과 같게 · DATA_006). 별칭 cl = coupang_links. */
    public static final String COUPANG_ON_SALE = "(cl.link_status = 'LINKED' AND COALESCE(cl.affiliate_url, '') <> ''"
            + " AND COALESCE(cl.missed_refreshes, 0) < " + com.example.nutriuniv.domain.coupang.entity.CoupangLink.SALE_UNKNOWN_AFTER + ")";

    /** WHERE 조각 (별칭 cl = coupang_links). */
    public String sql() {
        return this == COUPANG ? COUPANG_ON_SALE : "FALSE";
    }

    public static Seller from(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM,
                    "sellers는 COUPANG·CU·GS25·SEVEN_ELEVEN·EMART24·OLIVE_YOUNG·KURLY 중에서 고릅니다.");
        }
    }
}
