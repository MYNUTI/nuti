package com.example.nutriuniv.domain.product.dto;

import java.util.List;

/**
 * 바코드 조회 결과 분기 (v3 SCAN_010).
 * <ul>
 *   <li>RESULT — 분석 완료 제품 하나(단종 · 생산 중단 포함). result 에 결과 화면 전체</li>
 *   <li>MULTIPLE — 바코드에 제품이 둘 이상. candidates 에서 고른 뒤 GET /products/{id}</li>
 *   <li>NO_SCORE — 제품 하나인데 성분 7가지 중 빠진 것이 있음. result 는 이름 · 사진 · 성분까지(goals 빈 배열). 성분값 보강 요청 자동 등록</li>
 *   <li>CANDIDATE — 우리 제품에 없는 바코드인데 등록된 이름과 비슷한 같은 회사 제품이 있음(「이 제품인가요？」). candidates 에 1개, knownName 에 바코드 이름.
 *       맞아요 / 아니에요 → 검색에서 고른 제품은 POST /products/barcode/{barcode}/picks (kind=CONFIRM)</li>
 *   <li>NAME_ONLY — 우리 제품에 없고 비슷한 후보도 없지만 바코드 이름을 앎. knownName 에 이름 · 회사. 새 제품 요청 자동 등록</li>
 *   <li>NOT_FOUND — 어디에도 없음. 새 제품 요청 자동 등록</li>
 * </ul>
 * 분기 순서: 분석 완료 → 여러 개 → 등급 없음 → 비슷한 후보 → 이름만 아는 바코드 → 없음.
 * 내가 전에 고른 제품(여러 개 고르기 · 「맞아요」)이 있으면 다시 묻지 않고 RESULT · NO_SCORE 로 바로 연다. MULTIPLE 목록은 많이 고른 제품부터.
 */
public record ScanResultResponse(
        String outcome,
        String barcode,
        ProductResultResponse result,
        List<Candidate> candidates,
        KnownName knownName
) {

    /** 바코드에 등록된 제품명 · 회사 (CANDIDATE · NAME_ONLY). */
    public record KnownName(String name, String company) {}

    public record Candidate(Long productId, String name, String brandLine, String imageUrl, String state, String stateLabel) {}
}
