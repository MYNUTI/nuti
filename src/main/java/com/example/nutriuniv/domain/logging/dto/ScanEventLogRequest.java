package com.example.nutriuniv.domain.logging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /logging/scan-event 입력 — result: SCAN_FAIL | NOT_IN_DATA | CHECKSUM_FAIL | PERMISSION_DENIED. barcode 는 NOT_IN_DATA·CHECKSUM_FAIL 일 때만. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ScanEventLogRequest {

    private String result;
    private String barcode;
    private String surface;

    /** 서버가 바코드 조회에서 체크섬 불일치를 직접 기록할 때 (3.1 「그 사실을 기록한다」). */
    public static ScanEventLogRequest serverChecksumFail(String rawBarcode) {
        return new ScanEventLogRequest("CHECKSUM_FAIL", rawBarcode, "SERVER");
    }
}
