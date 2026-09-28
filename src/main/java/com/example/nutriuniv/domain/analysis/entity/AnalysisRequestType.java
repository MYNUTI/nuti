package com.example.nutriuniv.domain.analysis.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 분석 대기 유형 (기능명세서 10.2) — 둘을 섞지 않는다.
 * <ul>
 *   <li>NEW_PRODUCT — 식별값 바코드 또는 검색어. 스캔했는데 데이터에 없음(3.2)·검색 0건 폴백(6.1). 관리자가 제품을 새로 등록</li>
 *   <li>NUTRITION_FILL — 식별값 제품 ID. 영양정보 부족 제품 조회(2.3·5.1). 관리자가 7종을 채움</li>
 * </ul>
 */
public enum AnalysisRequestType {
    NEW_PRODUCT, NUTRITION_FILL;

    public static AnalysisRequestType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "type은 필수입니다 (NEW_PRODUCT | NUTRITION_FILL).");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "type은 NEW_PRODUCT 또는 NUTRITION_FILL 이어야 합니다.");
        }
    }
}
