package com.example.nutriuniv.domain.analysis.entity;

/**
 * 접수 경로 — 같은 요청이 어디서 얼마나 들어왔는지(수요 지표)와 일일 한도 계산에 쓴다.
 * <ul>
 *   <li>USER_BARCODE / USER_KEYWORD / USER_PRODUCT — 사용자 수동 접수 (POST /analysis-requests)</li>
 *   <li>USER_NAME — 제품명 요청 (v3 CTRB): 이름 필수 · 브랜드 선택. 늘 새 행. USER_KEYWORD 와 합쳐 하루 5건 한도(429)</li>
 *   <li>AUTO_SCAN — 바코드 스캔 404 / 영양정보 부족 자동 등록</li>
 *   <li>AUTO_RESULT — 결과 화면 조회 시 영양정보 부족 자동 등록</li>
 * </ul>
 */
public enum AnalysisRequestChannel {
    USER_BARCODE, USER_KEYWORD, USER_PRODUCT, USER_NAME, AUTO_SCAN, AUTO_RESULT
}
