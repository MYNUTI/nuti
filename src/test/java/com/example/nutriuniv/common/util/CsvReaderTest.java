package com.example.nutriuniv.common.util;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 적재 CSV 읽기 — 원재료명 칸의 쉼표·따옴표·줄바꿈, BOM, CRLF. */
class CsvReaderTest {

    private static List<CsvReader.Record> read(String s) throws Exception {
        return CsvReader.read(new StringReader(s));
    }

    @Test
    void 따옴표_칸_안의_쉼표와_따옴표를_그대로_읽는다() throws Exception {
        List<CsvReader.Record> r = read("a,b,c\n1,\"설탕, 소금, \"\"천연\"\" 향료\",3\n");
        assertEquals(2, r.size());
        assertEquals(List.of("1", "설탕, 소금, \"천연\" 향료", "3"), r.get(1).fields());
    }

    @Test
    void BOM_을_버리고_CRLF_를_줄바꿈으로_본다() throws Exception {
        List<CsvReader.Record> r = read("﻿major_id,major_name\r\n01,다이어트·단백질\r\n");
        assertEquals("major_id", r.get(0).get(0));
        assertEquals(List.of("01", "다이어트·단백질"), r.get(1).fields());
    }

    @Test
    void 칸_안의_줄바꿈은_한_레코드로_읽고_줄_번호는_시작_줄이다() throws Exception {
        List<CsvReader.Record> r = read("h1,h2\n\"첫 줄\n둘째 줄\",x\nlast,y");
        assertEquals(3, r.size());
        assertEquals("첫 줄\n둘째 줄", r.get(1).get(0));
        assertEquals(2, r.get(1).line());
        assertEquals(4, r.get(2).line());
    }

    @Test
    void 빈_칸과_빈_줄() throws Exception {
        List<CsvReader.Record> r = read("a,b,c\n\n1,,\n");
        assertEquals(2, r.size());
        assertEquals(List.of("1", "", ""), r.get(1).fields());
        assertEquals("", r.get(1).get(9));   // 없는 칸은 빈 값
    }
}
