package com.example.nutriuniv.domain.category.service;

import com.example.nutriuniv.common.util.CsvReader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 분류 시드 파일 (v3 DATA_005) — 보이는 대분류 15 · 중분류 103, 숨긴 기타(16) 4개, 코드 형식. */
class CategorySeederTest {

    @Test
    void 들어_있는_시드_파일은_보이는_대분류_15개_중분류_103개와_숨긴_기타_4개() throws Exception {
        try (Reader r = new InputStreamReader(new ClassPathResource(CategorySeeder.SEED_PATH).getInputStream(), StandardCharsets.UTF_8)) {
            List<CategorySeeder.SeedRow> rows = CategorySeeder.parse(CsvReader.read(r));
            List<CategorySeeder.SeedRow> shown = rows.stream().filter(x -> !x.hidden()).toList();
            assertEquals(103, shown.size());
            assertEquals(15, shown.stream().map(CategorySeeder.SeedRow::majorCode).collect(Collectors.toSet()).size());
            assertEquals("다이어트·단백질", rows.get(0).majorName());
            assertEquals("0101", rows.get(0).minorCode());

            List<CategorySeeder.SeedRow> etc = rows.stream().filter(CategorySeeder.SeedRow::hidden).toList();
            assertEquals(List.of("1601", "1602", "1603", "1604"), etc.stream().map(CategorySeeder.SeedRow::minorCode).toList());
            assertTrue(etc.stream().allMatch(x -> x.majorCode().equals("16") && x.majorName().equals("기타")));
            // 술 · 영유아식 · 환자용 식품은 점수 제외, 분류 못 정함은 점수를 매긴다
            assertEquals(List.of(true, true, true, false), etc.stream().map(CategorySeeder.SeedRow::scoreExcluded).toList());
        }
    }

    @Test
    void 숨김_점수_제외_열은_없어도_되고_점수_제외는_숨김이어야_한다() throws Exception {
        List<CategorySeeder.SeedRow> plain = CategorySeeder.parse(CsvReader.read(new StringReader(
                "major_id,major_name,minor_id,minor_name\n01,다이어트,0101,파우더\n")));
        assertFalse(plain.get(0).hidden());
        assertFalse(plain.get(0).scoreExcluded());

        String bad = "major_id,major_name,minor_id,minor_name,is_hidden,is_score_excluded\n99,기타,9901,술,,Y\n";
        assertThrows(IllegalStateException.class, () -> CategorySeeder.parse(CsvReader.read(new StringReader(bad))));
    }

    @Test
    void 엑셀이_지운_앞_0_을_채운다() {
        assertEquals("01", CategorySeeder.pad("1", 2));
        assertEquals("0101", CategorySeeder.pad("101", 4));
        assertEquals("1209", CategorySeeder.pad(" 1209 ", 4));
        assertThrows(IllegalStateException.class, () -> CategorySeeder.pad("12a", 4));
        assertThrows(IllegalStateException.class, () -> CategorySeeder.pad("12345", 4));
    }

    @Test
    void 대분류와_안_맞는_중분류_코드와_겹치는_코드는_시드_오류() throws Exception {
        String bad = "major_id,major_name,minor_id,minor_name\n02,간편식,0301,주스\n";
        assertThrows(IllegalStateException.class, () -> CategorySeeder.parse(CsvReader.read(new StringReader(bad))));
        String dup = "major_id,major_name,minor_id,minor_name\n02,간편식,0201,국\n02,간편식,0201,탕\n";
        assertThrows(IllegalStateException.class, () -> CategorySeeder.parse(CsvReader.read(new StringReader(dup))));
    }
}
