package com.example.nutriuniv.domain.product.util;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

import java.util.Optional;

/**
 * 바코드 정규화 (기능명세서 3.1) — 8/12/13/14자리 → 14자리(GTIN-14). <b>저장할 때와 찾을 때 같은 함수를 쓴다.</b>
 * <ul>
 *   <li>14자리(GTIN-14): 그대로</li>
 *   <li>13자리(EAN-13)·12자리(UPC-A)·8자리(EAN-8): 왼쪽에 0을 채워 14자리로</li>
 *   <li>체크섬(GTIN mod 10)이 안 맞으면 400 BARCODE_CHECKSUM_INVALID — 조회하지 않고 재촬영 안내</li>
 * </ul>
 * 0 을 왼쪽에 채워도 가중치(오른쪽 끝 체크 숫자 바로 앞부터 3,1 교대)가 오른쪽 끝 기준으로 정해지므로
 * EAN-8·UPC-A·EAN-13 의 원래 체크섬과 같은 검사가 된다.
 * 공공데이터 34컬럼(product_master)의 barcode 도 14자리라 변환 없이 그대로 비교할 수 있다.
 * 순수 함수 — 테스트(BarcodeNormalizerTest)로 고정.
 */
public final class BarcodeNormalizer {

    public static final int LENGTH = 14;

    private BarcodeNormalizer() {}

    /** 정규화된 14자리. 형식 오류는 400 BAD_REQUEST, 체크섬 오류는 400 BARCODE_CHECKSUM_INVALID. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "바코드가 비어 있습니다.");
        }
        String s = raw.trim();
        if (!s.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "바코드는 숫자만 가능합니다.");
        }
        String fourteen = switch (s.length()) {
            case 14 -> s;
            case 13, 12, 8 -> "0".repeat(LENGTH - s.length()) + s;
            default -> throw new CustomException(ErrorCode.BAD_REQUEST, "바코드는 8·12·13·14자리여야 합니다.");
        };
        if (!isValidGtin14(fourteen)) {
            throw new CustomException(ErrorCode.BARCODE_CHECKSUM_INVALID);
        }
        return fourteen;
    }

    /** 예외 대신 Optional — 적재처럼 한 값이 틀려도 행을 살려야 하는 곳용. */
    public static Optional<String> tryNormalize(String raw) {
        try {
            return Optional.of(normalize(raw));
        } catch (CustomException e) {
            return Optional.empty();
        }
    }

    /** GTIN-14 체크섬: 왼쪽부터 13자리에 3,1 가중치 교대 합 → (10 − 합 mod 10) mod 10 == 14번째 자리. */
    public static boolean isValidGtin14(String d14) {
        if (d14 == null || d14.length() != LENGTH) return false;
        int sum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            int digit = d14.charAt(i) - '0';
            if (digit < 0 || digit > 9) return false;
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == d14.charAt(LENGTH - 1) - '0';
    }
}