package com.example.nutriuniv.domain.link.entity;

/**
 * 구매 링크 매칭 종류 (DATA_006 · RSLT_009) — 화면 가격 줄과 버튼이 이 값으로 갈린다.
 * <ul>
 *   <li>EXACT — 같은 용량. 상품 페이지 + 판매가 · 기준일 · 100g(mL)당 · 1개당</li>
 *   <li>OTHER_SIZE — 다른 용량(묶음 등). 상품 페이지 + 묶음 수 · 1개당(못 읽으면 「다른 용량」만). 판매가는 주지 않는다</li>
 *   <li>NAME_SEARCH — 용량을 확인하지 못한 예전 연결. 쿠팡 검색 결과 페이지로, 가격은 주지 않는다</li>
 *   <li>NONE — 링크 없음(정상 응답)</li>
 * </ul>
 */
public enum PurchaseMatchType {
    EXACT, OTHER_SIZE, NAME_SEARCH, NONE;

    /** coupang_links.match_type 운영자 지정값 → enum. 비어 있거나 모르는 값은 null(자동 판정). */
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
