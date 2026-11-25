package com.example.nutriuniv.domain.category.service;

import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 분류 목록 시드 (v3 DATA_005) — 부팅 때 db/seed/categories.csv 로 대분류·중분류를 코드 기준으로 맞춘다.
 * 없으면 만들고, 이름·순서·표식이 바뀌었으면 고친다. 파일에 없는 코드 행은 건드리지 않는다(제품이 물려 있을 수 있다). 재실행해도 같다.
 * 열: major_id, major_name, minor_id, minor_name, is_hidden, is_score_excluded (뒤 두 열은 Y 면 켬, 없거나 비면 끔).
 * 대분류는 그 아래 중분류가 모두 숨김이면 숨긴다(「기타」). 노출 순서는 파일 순서.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    static final String SEED_PATH = "db/seed/categories.csv";

    private final CategoryRepository categoryRepository;

    /** 파일 한 줄 = 중분류 하나와 그 대분류. */
    record SeedRow(String majorCode, String majorName, String minorCode, String minorName, boolean hidden, boolean scoreExcluded) {
        SeedRow(String majorCode, String majorName, String minorCode, String minorName) {
            this(majorCode, majorName, minorCode, minorName, false, false);
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        List<SeedRow> rows;
        try (Reader r = new InputStreamReader(new ClassPathResource(SEED_PATH).getInputStream(), StandardCharsets.UTF_8)) {
            rows = parse(CsvReader.read(r));
        }
        Map<String, Category> byCode = new HashMap<>();
        for (Category c : categoryRepository.findAllByCodeIsNotNull()) byCode.put(c.getCode(), c);

        Map<String, Boolean> majorHidden = new HashMap<>();
        for (SeedRow row : rows) majorHidden.merge(row.majorCode(), row.hidden(), Boolean::logicalAnd);

        int created = 0, changed = 0;
        Map<String, Integer> majorOrder = new LinkedHashMap<>();
        Map<String, Integer> minorOrderInMajor = new HashMap<>();
        for (SeedRow row : rows) {
            boolean newMajor = !majorOrder.containsKey(row.majorCode());
            int mOrder = majorOrder.computeIfAbsent(row.majorCode(), k -> majorOrder.size() + 1);
            boolean hiddenMajor = majorHidden.get(row.majorCode());
            Category major = byCode.get(row.majorCode());
            if (major == null) {
                major = categoryRepository.save(Category.seeded(row.majorCode(), row.majorName(), null, mOrder, hiddenMajor, false));
                byCode.put(row.majorCode(), major);
                created++;
            } else if (newMajor && major.applySeed(row.majorName(), null, mOrder, hiddenMajor, false)) {
                changed++;
            }

            int order = minorOrderInMajor.merge(row.majorCode(), 1, Integer::sum);
            Category minor = byCode.get(row.minorCode());
            if (minor == null) {
                byCode.put(row.minorCode(), categoryRepository.save(
                        Category.seeded(row.minorCode(), row.minorName(), major, order, row.hidden(), row.scoreExcluded())));
                created++;
            } else if (minor.applySeed(row.minorName(), major, order, row.hidden(), row.scoreExcluded())) {
                changed++;
            }
        }
        if (created + changed > 0) {
            log.info("[CategorySeeder] 분류 시드 반영 — 새로 {}건, 수정 {}건 (파일 {}줄)", created, changed, rows.size());
        }
    }

    /** 헤더 1줄 + 데이터. 코드는 숫자만, 엑셀이 앞 0 을 지운 값(1 → 01, 101 → 0101)도 받는다. */
    static List<SeedRow> parse(List<CsvReader.Record> records) {
        if (records.isEmpty()) throw new IllegalStateException(SEED_PATH + " 가 비어 있습니다.");
        List<String> header = records.get(0).fields().stream().map(String::trim).toList();
        int mi = indexOf(header, "major_id"), mn = indexOf(header, "major_name");
        int ni = indexOf(header, "minor_id"), nn = indexOf(header, "minor_name");
        int hi = header.indexOf("is_hidden"), si = header.indexOf("is_score_excluded");   // 없어도 됨
        List<SeedRow> rows = new ArrayList<>();
        Set<String> seenMinor = new HashSet<>();
        for (CsvReader.Record rec : records.subList(1, records.size())) {
            String majorCode = pad(rec.get(mi), 2);
            String minorCode = pad(rec.get(ni), 4);
            String majorName = rec.get(mn).trim();
            String minorName = rec.get(nn).trim();
            if (majorName.isEmpty() || minorName.isEmpty()) {
                throw new IllegalStateException(SEED_PATH + " " + rec.line() + "줄: 이름이 비어 있습니다.");
            }
            if (!minorCode.startsWith(majorCode)) {
                throw new IllegalStateException(SEED_PATH + " " + rec.line() + "줄: 중분류 코드 " + minorCode + " 가 대분류 " + majorCode + " 로 시작하지 않습니다.");
            }
            if (!seenMinor.add(minorCode)) {
                throw new IllegalStateException(SEED_PATH + " " + rec.line() + "줄: 중분류 코드 " + minorCode + " 가 겹칩니다.");
            }
            boolean hidden = flag(rec, hi), scoreExcluded = flag(rec, si);
            if (scoreExcluded && !hidden) {
                throw new IllegalStateException(SEED_PATH + " " + rec.line() + "줄: 점수를 매기지 않는 중분류는 숨김이어야 합니다.");
            }
            rows.add(new SeedRow(majorCode, majorName, minorCode, minorName, hidden, scoreExcluded));
        }
        return rows;
    }

    private static boolean flag(CsvReader.Record rec, int index) {
        return index >= 0 && "Y".equalsIgnoreCase(rec.get(index).trim());
    }

    private static int indexOf(List<String> header, String name) {
        int i = header.indexOf(name);
        if (i < 0) throw new IllegalStateException(SEED_PATH + " 에 " + name + " 열이 없습니다.");
        return i;
    }

    /** 숫자 코드를 자리수에 맞춘다. 숫자가 아니거나 너무 길면 시드 파일 오류. */
    static String pad(String raw, int width) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty() || s.length() > width || !s.chars().allMatch(Character::isDigit)) {
            throw new IllegalStateException(SEED_PATH + ": 분류 코드 형식 오류 — '" + raw + "'");
        }
        return "0".repeat(width - s.length()) + s;
    }
}
