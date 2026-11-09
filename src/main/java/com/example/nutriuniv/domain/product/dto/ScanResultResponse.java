package com.example.nutriuniv.domain.product.dto;

import java.util.List;

/**
 * 바코드 조회 결과 분기 (v3 SCAN_010).
 * <ul>
 *   <li>RESULT — 분석 완료 제품 하나(단종 · 생산 중단 포함). result 에 결과 화면 전체</li>
 *   <li>MULTIPLE — 바코드에 제품이 둘 이상. candidates 에서 고른 뒤 GET /products/{id}</li>
 *   <li>NO_SCORE — 제품 하나인데 성분 7가지 중 빠진 것이 있음. result 는 이름 · 사진 · 성분까지(goals 빈 배열). 성분값 보강 요청 자동 등록</li>
 *   <li>NOT_FOUND — 어디에도 없음. 새 제품 요청 자동 등록</li>
 * </ul>
 */
public record ScanResultResponse(
        String outcome,
        String barcode,
        ProductResultResponse result,
        List<Candidate> candidates
) {

    public record Candidate(Long productId, String name, String brandLine, String imageUrl, String state, String stateLabel) {}
}
