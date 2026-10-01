package com.example.nutriuniv.domain.link.entity;

/**
 * 구매 링크 매칭 종류 (기능명세서 8.1) — 화면 문구가 이 값으로 갈린다.
 * <ul>
 *   <li>EXACT — 정확히 같은 제품. 「구매하기」 + 가격(24시간 이내 조회분만)</li>
 *   <li>NAME_SEARCH — 이름으로 찾은 것. 「이 제품을 검색해 보기」. 가격은 절대 주지 않는다(다른 제품 가격을 이 제품 가격처럼 보이게 된다)</li>
 *   <li>NONE — 링크 없음(정상 응답)</li>
 * </ul>
 */
public enum PurchaseMatchType {
    EXACT, NAME_SEARCH, NONE;

    /** coupang_links.match_type 수동 지정값 → enum. 비어 있거나 모르는 값은 null(자동 판정). */
    public static PurchaseMatchType fromOverride(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            PurchaseMatchType t = valueOf(raw.trim().toUpperCase());
            return t == NONE ? null : t;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
