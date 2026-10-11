package com.example.nutriuniv.domain.analysis.entity;

/**
 * 접수 경로 — 같은 대기 항목이 어디서 얼마나 요청됐는지(수요 지표)와 일일 한도 계산에 쓴다.
 * <ul>
 *   <li>USER_BARCODE / USER_KEYWORD / USER_PRODUCT — 사용자 수동 접수 (POST /analysis-requests). 검색어 접수만 하루 5건 한도(429)</li>
 *   <li>AUTO_SCAN — 바코드 스캔 404 / 영양정보 부족 자동 등록 (3.2)</li>
 *   <li>AUTO_RESULT — 결과 화면 조회 시 영양정보 부족 자동 등록 (2.3·5.1)</li>
 * </ul>
 */
public enum AnalysisRequestChannel {
    USER_BARCODE, USER_KEYWORD, USER_PRODUCT, AUTO_SCAN, AUTO_RESULT
}
