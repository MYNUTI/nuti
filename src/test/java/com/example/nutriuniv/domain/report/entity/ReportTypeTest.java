package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** v3 CTRB_008 — 제보 항목 4종. 코드 · 한글 이름 · 옛 값(NAME · IMAGE) 모두 받는다. */
class ReportTypeTest {

    @Test
    void 코드와_한글_이름() {
        assertEquals(ReportType.NUTRITION, ReportType.from("NUTRITION"));
        assertEquals(ReportType.WRONG_PRODUCT, ReportType.from(" wrong_product "));
        assertEquals(ReportType.NAME_OR_IMAGE, ReportType.from("이름이나 사진이 달라요"));
        assertEquals(ReportType.OTHER, ReportType.from("기타"));
    }

    @Test
    void 옛_값은_새_항목으로() {
        assertEquals(ReportType.NAME_OR_IMAGE, ReportType.from("NAME"));
        assertEquals(ReportType.NAME_OR_IMAGE, ReportType.from(" image "));
        assertEquals(ReportType.NAME_OR_IMAGE, ReportType.from("제품명"));
        assertEquals(ReportType.NUTRITION, ReportType.from("영양정보"));
    }

    @Test
    void 허용값_외_400() {
        CustomException e = assertThrows(CustomException.class, () -> ReportType.from("PRICE"));
        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
        assertThrows(CustomException.class, () -> ReportType.from(null));
        assertThrows(CustomException.class, () -> ReportType.from(""));
    }

    @Test
    void 기타는_데이터_수정이_아니다() {
        assertTrue(ReportType.NUTRITION.correctsData());
        assertTrue(ReportType.WRONG_PRODUCT.correctsData());
        assertTrue(ReportType.NAME_OR_IMAGE.correctsData());
        assertFalse(ReportType.OTHER.correctsData());
    }

    @Test
    void 제보_상태_옛_PROCESSING_은_확인_중() {
        assertEquals(ReportStatus.RECEIVED, ReportStatus.from("processing"));
        assertTrue(ReportStatus.REJECTED.isTerminal());
        assertFalse(ReportStatus.RECEIVED.isTerminal());
    }
}
