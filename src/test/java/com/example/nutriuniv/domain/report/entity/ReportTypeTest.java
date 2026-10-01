package com.example.nutriuniv.domain.report.entity;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 기능명세서 10.4 — 제보 유형 4종, 코드·한글 이름 둘 다 허용. */
class ReportTypeTest {

    @Test
    void 코드와_한글_이름() {
        assertEquals(ReportType.NUTRITION, ReportType.from("NUTRITION"));
        assertEquals(ReportType.NUTRITION, ReportType.from("영양정보"));
        assertEquals(ReportType.NAME, ReportType.from(" name "));
        assertEquals(ReportType.IMAGE, ReportType.from("이미지"));
        assertEquals(ReportType.OTHER, ReportType.from("기타"));
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
        assertTrue(ReportType.NAME.correctsData());
        assertTrue(ReportType.IMAGE.correctsData());
        assertFalse(ReportType.OTHER.correctsData());
    }
}
