package com.example.nutriuniv.common.util;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV 읽기 — 큰따옴표 칸 안의 쉼표·줄바꿈·"" 를 처리한다(원재료명 칸에 쉼표가 많다).
 * 맨 앞 BOM 은 버린다(엑셀이 UTF-8 로 저장하면 붙인다). 빈 줄은 건너뛴다. 순수 함수 — CsvReaderTest 로 고정.
 */
public final class CsvReader {

    /** @param line 이 레코드가 시작한 파일 줄 번호(1부터) — 실패 행 안내용 */
    public record Record(int line, List<String> fields) {
        public String get(int index) {
            return index >= 0 && index < fields.size() ? fields.get(index) : "";
        }
    }

    private CsvReader() {}

    public static List<Record> read(Reader in) throws IOException {
        List<Record> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellStarted = false;
        int line = 1;
        int recordLine = 1;
        int c = in.read();
        if (c == '﻿') c = in.read();

        while (c != -1) {
            if (quoted) {
                if (c == '"') {
                    int next = in.read();
                    if (next == '"') {
                        cell.append('"');
                    } else {
                        quoted = false;
                        c = next;
                        continue;
                    }
                } else {
                    if (c == '\n') line++;
                    cell.append((char) c);
                }
            } else if (c == '"' && !cellStarted) {
                quoted = true;
                cellStarted = true;
            } else if (c == ',') {
                fields.add(cell.toString());
                cell.setLength(0);
                cellStarted = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r') {
                    int next = in.read();
                    if (next != '\n') {
                        endRecord(records, fields, cell, recordLine);
                        line++;
                        recordLine = line;
                        cellStarted = false;
                        c = next;
                        continue;
                    }
                }
                endRecord(records, fields, cell, recordLine);
                line++;
                recordLine = line;
                cellStarted = false;
            } else {
                cell.append((char) c);
                cellStarted = true;
            }
            c = in.read();
        }
        endRecord(records, fields, cell, recordLine);
        return records;
    }

    private static void endRecord(List<Record> records, List<String> fields, StringBuilder cell, int recordLine) {
        fields.add(cell.toString());
        cell.setLength(0);
        boolean blank = fields.stream().allMatch(String::isBlank);
        if (!blank) records.add(new Record(recordLine, List.copyOf(fields)));
        fields.clear();
    }
}
