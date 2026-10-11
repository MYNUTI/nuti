package com.example.nutriuniv.domain.product.util;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;

import java.util.Optional;

/**
 * 바코드 정규화 (기능명세서 3.1) — 8/12/13/14자리 → 13자리. <b>저장할 때와 찾을 때 같은 함수를 쓴다.</b>
 * <ul>
 *   <li>14자리(GTIN-14): 앞의 0을 뗀다. 앞자리가 0이 아니면 EAN-13 으로 표현할 수 없어 400</li>
 *   <li>12자리(UPC-A)·8자리(EAN-8): 왼쪽에 0을 채운다</li>
 *   <li>13자리: 그대로</li>
 *   <li>체크섬(EAN-13 mod 10)이 안 맞으면 400 BARCODE_CHECKSUM_INVALID — 조회하지 않고 재촬영 안내</li>
 * </ul>
 * 0 을 왼쪽에 채워도 가중치(1,3 교대)가 오른쪽 끝 기준으로 정해지므로 EAN-8·UPC-A·GTIN-14 의 원래 체크섬과 같은 검사가 된다.
 * 순수 함수 — 테스트(BarcodeNormalizerTest)로 고정.
 */
public final class BarcodeNormalizer {

    public static final int LENGTH = 13;

    private BarcodeNormalizer() {}

    /** 정규화된 13자리. 형식 오류는 400 BAD_REQUEST, 체크섬 오류는 400 BARCODE_CHECKSUM_INVALID. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "바코드가 비어 있습니다.");
        }
        String s = raw.trim();
        if (!s.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "바코드는 숫자만 가능합니다.");
        }
        String thirteen = switch (s.length()) {
            case 13 -> s;
            case 14 -> {
                if (s.charAt(0) != '0') {
                    throw new CustomException(ErrorCode.BAD_REQUEST, "14자리 바코드는 앞자리가 0이어야 합니다.");
                }
                yield s.substring(1);
            }
            case 12, 8 -> "0".repeat(LENGTH - s.length()) + s;
            default -> throw new CustomException(ErrorCode.BAD_REQUEST, "바코드는 8·12·13·14자리여야 합니다.");
        };
        if (!isValidEan13(thirteen)) {
            throw new CustomException(ErrorCode.BARCODE_CHECKSUM_INVALID);
        }
        return thirteen;
    }

    /** 예외 대신 Optional — 엑셀 적재처럼 한 값이 틀려도 행을 살려야 하는 곳용. */
    public static Optional<String> tryNormalize(String raw) {
        try {
            return Optional.of(normalize(raw));
        } catch (CustomException e) {
            return Optional.empty();
        }
    }

    /** EAN-13 체크섬: 왼쪽부터 12자리에 1,3 가중치 교대 합 → (10 − 합 mod 10) mod 10 == 13번째 자리. */
    public static boolean isValidEan13(String d13) {
        if (d13 == null || d13.length() != LENGTH) return false;
        int sum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            int digit = d13.charAt(i) - '0';
            if (digit < 0 || digit > 9) return false;
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == d13.charAt(LENGTH - 1) - '0';
    }
}
