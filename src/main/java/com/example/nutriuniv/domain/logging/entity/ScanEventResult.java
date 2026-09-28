package com.example.nutriuniv.domain.logging.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

/**
 * 스캔 실패 원인 4종 (기능명세서 3.2·9.1) — 「인식 실패」와 「데이터에 없음」은 원인이 다르므로 화면도 로그도 분리한다.
 * 바코드는 인식이 된 경우(NOT_IN_DATA·CHECKSUM_FAIL)에만 남긴다.
 */
public enum ScanEventResult {
    SCAN_FAIL,          // 카메라·조명·곡면 — 인식 자체 실패
    NOT_IN_DATA,        // 인식은 됐는데 우리 데이터에 없음
    CHECKSUM_FAIL,      // 체크섬 불일치 — 조회하지 않음
    PERMISSION_DENIED;  // 카메라 권한 거부

    public boolean keepsBarcode() {
        return this == NOT_IN_DATA || this == CHECKSUM_FAIL;
    }

    public static ScanEventResult from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "result는 필수입니다 (SCAN_FAIL | NOT_IN_DATA | CHECKSUM_FAIL | PERMISSION_DENIED).");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "result는 SCAN_FAIL·NOT_IN_DATA·CHECKSUM_FAIL·PERMISSION_DENIED 중 하나여야 합니다.");
        }
    }
}
