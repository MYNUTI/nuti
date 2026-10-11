package com.example.nutriuniv.domain.product.util;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 기능명세서 3.1 이 요구하는 고정 케이스 — 14자리 / 13자리 정상 / 8자리 체크섬 실패 / 숫자 아닌 문자 / 빈 값 + UPC-A·EAN-8 정상.
 * 8801234567893: 880123456789 의 EAN-13 체크 자리 = 3.
 */
class BarcodeNormalizerTest {

    @Test
    void 열세자리_정상은_그대로() {
        assertEquals("8801234567893", BarcodeNormalizer.normalize("8801234567893"));
        assertEquals("8801234567893", BarcodeNormalizer.normalize("  8801234567893 "));
    }

    @Test
    void 열네자리는_앞_0을_뗀다() {
        assertEquals("8801234567893", BarcodeNormalizer.normalize("08801234567893"));
        CustomException e = assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize("18801234567893"));
        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
    }

    @Test
    void 열두자리_UPC와_여덟자리_EAN8은_왼쪽에_0을_채운다() {
        assertEquals("0036000291452", BarcodeNormalizer.normalize("036000291452"));   // UPC-A
        assertEquals("0000096385074", BarcodeNormalizer.normalize("96385074"));       // EAN-8
    }

    @Test
    void 체크섬_불일치는_BARCODE_CHECKSUM_INVALID() {
        CustomException e8 = assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize("96385075"));
        assertEquals(ErrorCode.BARCODE_CHECKSUM_INVALID, e8.getErrorCode());
        CustomException e13 = assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize("8801234567891"));
        assertEquals(ErrorCode.BARCODE_CHECKSUM_INVALID, e13.getErrorCode());
    }

    @Test
    void 숫자_아닌_문자_빈_값_지원하지_않는_길이는_BAD_REQUEST() {
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize("88012345678A3")).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize("")).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize(null)).getErrorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CustomException.class, () -> BarcodeNormalizer.normalize("1234567890")).getErrorCode());
    }

    @Test
    void 저장과_조회가_같은_함수를_쓰므로_두_번_적용해도_같다() {
        String once = BarcodeNormalizer.normalize("08801234567893");
        assertEquals(once, BarcodeNormalizer.normalize(once));
    }

    @Test
    void tryNormalize는_예외_대신_empty() {
        assertTrue(BarcodeNormalizer.tryNormalize("8801234567893").isPresent());
        assertTrue(BarcodeNormalizer.tryNormalize("abc").isEmpty());
        assertTrue(BarcodeNormalizer.tryNormalize("8801234567891").isEmpty());
    }
}
