package com.example.nutriuniv.domain.publicdata.brand;

import com.example.nutriuniv.common.util.CsvReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * brand 칸 계산용 고정 조회표 — 데이터 팀이 만든 CSV 를 부팅 때 메모리로 읽는다(합쳐 3만 행 남짓).
 * 표를 고치면 resources/publicdata/brand/ 의 파일을 바꿔 커밋한다 — 무엇이 언제 바뀌었는지 git 에 남는다.
 * <table>
 *   <tr><th>파일</th><th>원본</th><th>쓰는 곳 (brand.md 계산 순서)</th></tr>
 *   <tr><td>brand_dictionary.csv</td><td>1_브랜드사전_2026-11-09.csv</td><td>3 제품명 첫 낱말 → 브랜드</td></tr>
 *   <tr><td>company_labels.csv</td><td>2_회사이름표_2026-10-29.csv</td><td>7 회사 이름 → 이름표 · 정한 곳</td></tr>
 *   <tr><td>company_merge.csv</td><td>3_같은회사_합치기표_2026-10-29.csv</td><td>8 2단계 합치기(화면 표시 이름)</td></tr>
 *   <tr><td>coupang_fix.csv</td><td>4_쿠팡보정_고정값_2026-11-09.csv</td><td>2 묶음 키 → 고정 브랜드</td></tr>
 *   <tr><td>fix_1112.csv (선택)</td><td>통합 마스터에서 추출</td><td>1 11-12 수동 고침 (빈 값 = 비움)</td></tr>
 *   <tr><td>coupang_names.csv (선택)</td><td>통합 마스터에서 추출</td><td>7 「둘 다」 판정의 쿠팡 상품명</td></tr>
 * </table>
 * 필수 파일이 없거나 헤더가 틀려도 서버는 뜬다(이 표는 공공데이터 최신화에서만 쓴다) — 대신 모든 표를 비우고 {@link #isReady()} 가 false 가 된다.
 * brand 를 계산하는 쪽은 isReady() 가 false 면 계산을 거부해야 한다(틀린 표로 brand 를 조용히 비우지 않게). 선택 파일은 없으면 빈 표.
 */
@Slf4j
@Component
public class BrandLookupTables {

    static final String DIR = "publicdata/brand/";

    /** 이름표에서 믿을 만한 「정한 곳」 — 이 셋이 아니면(규칙 · AI 판정(확신 낮음) · 신호 없음 …) 브랜드로 보지 않는다. */
    public static final Set<String> TRUSTED_DECIDED_BY = Set.of("10-28 웹 판정", "AI 판정", "브랜드 사전");

    public static final String LABEL_BRAND = "브랜드";
    public static final String LABEL_RETAILER = "유통사";
    public static final String LABEL_BOTH = "둘 다";

    /** 회사 이름표 한 줄. */
    public record CompanyLabel(String label, String decidedBy) {
        public boolean trusted() {
            return TRUSTED_DECIDED_BY.contains(decidedBy);
        }
    }

    private final Map<String, String> dictionary;              // 정규화 낱말 → 브랜드
    private final List<Map.Entry<String, String>> prefixWords; // 붙여 써도 쓰는 낱말(2글자 이상), 긴 것부터
    private final Map<String, CompanyLabel> companyLabels;     // 회사 이름 → 이름표
    private final Map<String, String> merge2;                  // 원래 이름 → 보일 이름
    private final Map<String, String> coupangFix;              // 묶음 키 → 브랜드
    private final Map<String, String> fix1112;                 // 묶음 키 → 브랜드 (빈 문자열 = 비움)
    private final Map<String, String> coupangNames;            // 묶음 키 → 쿠팡 상품명
    private final boolean ready;
    private final String problem;                              // 준비 안 된 이유 (ready 면 null)

    public BrandLookupTables() {
        this(DIR);
    }

    BrandLookupTables(String dir) {
        Tables t;
        String failure = null;
        try {
            t = load(dir);
        } catch (RuntimeException e) {
            failure = e.getMessage();
            log.error("[BRAND] 조회표를 읽지 못해 브랜드 계산을 쓸 수 없습니다 — {}. 서버는 계속 뜹니다. "
                    + "src/main/resources/{} 에 파일이 있는지 확인하세요", e.getMessage(), dir);
            t = Tables.EMPTY;
        }
        this.dictionary = t.dictionary();
        this.prefixWords = t.prefixWords();
        this.companyLabels = t.companyLabels();
        this.merge2 = t.merge2();
        this.coupangFix = t.coupangFix();
        this.fix1112 = t.fix1112();
        this.coupangNames = t.coupangNames();
        this.ready = failure == null;
        this.problem = failure;
        if (ready) {
            log.info("[BRAND] 조회표 — 사전 {} (붙여쓰기 {}) · 회사 이름표 {} · 합치기 {} · 쿠팡 보정 {} · 11-12 고침 {} · 쿠팡 상품명 {}",
                    dictionary.size(), prefixWords.size(), companyLabels.size(), merge2.size(),
                    coupangFix.size(), fix1112.size(), coupangNames.size());
        }
    }

    /** 표 묶음 — 읽기에 실패하면 EMPTY. */
    private record Tables(Map<String, String> dictionary, List<Map.Entry<String, String>> prefixWords,
                          Map<String, CompanyLabel> companyLabels, Map<String, String> merge2,
                          Map<String, String> coupangFix, Map<String, String> fix1112, Map<String, String> coupangNames) {
        static final Tables EMPTY = new Tables(Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static Tables load(String dir) {
        Map<String, String> dict = new HashMap<>();
        List<Map.Entry<String, String>> prefix = new ArrayList<>();
        for (Map<String, String> r : read(dir + "brand_dictionary.csv", true, "낱말(정규화)", "브랜드(표시 이름)", "붙여 쓴 이름에도 씀")) {
            String word = r.get("낱말(정규화)").trim();
            String brand = r.get("브랜드(표시 이름)").trim();
            if (word.isEmpty() || brand.isEmpty()) continue;
            dict.putIfAbsent(word, brand);                 // 같은 낱말이 두 줄이면 값이 같다(브랜드 사전 설명) — 첫 줄
            if ("Y".equalsIgnoreCase(r.get("붙여 쓴 이름에도 씀").trim()) && word.length() >= 2) {
                prefix.add(Map.entry(word, brand));
            }
        }
        prefix.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed()
                .thenComparing(Map.Entry::getKey));

        Map<String, CompanyLabel> labels = new HashMap<>();
        for (Map<String, String> r : read(dir + "company_labels.csv", true, "이름", "이름표", "정한 곳")) {
            String name = r.get("이름").trim();
            if (!name.isEmpty()) labels.putIfAbsent(name, new CompanyLabel(r.get("이름표").trim(), r.get("정한 곳").trim()));
        }

        Map<String, String> merge = new HashMap<>();
        for (Map<String, String> r : read(dir + "company_merge.csv", true, "원래 이름", "보일 이름")) {
            String from = r.get("원래 이름").trim();
            String to = r.get("보일 이름").trim();
            if (!from.isEmpty() && !to.isEmpty()) merge.putIfAbsent(from, to);
        }

        return new Tables(
                Map.copyOf(dict),
                List.copyOf(prefix),
                Map.copyOf(labels),
                Map.copyOf(merge),
                keyed(read(dir + "coupang_fix.csv", true, "영양 묶음 키", "브랜드(지금 값)"), "영양 묶음 키", "브랜드(지금 값)", false),
                keyed(read(dir + "fix_1112.csv", false, "묶음 키", "브랜드"), "묶음 키", "브랜드", true),
                keyed(read(dir + "coupang_names.csv", false, "묶음 키", "쿠팡 상품명"), "묶음 키", "쿠팡 상품명", false));
    }

    // ── 상태 ──────────────────────────────────────────────────────────────────

    /** 필수 조회표를 모두 읽었는가. false 면 brand 계산을 하지 않는다. */
    public boolean isReady() {
        return ready;
    }

    /** 준비 안 된 이유 — 관리자 응답 · 로그용. 준비됐으면 null. */
    public String problem() {
        return problem;
    }

    // ── 조회 ──────────────────────────────────────────────────────────────────

    /**
     * 브랜드 사전 (brand.md dictLookup). w 는 compact 한 제품명 첫 낱말.
     * 사전에 그대로 있으면 그 브랜드. 없으면 「붙여 써도 씀」 낱말을 긴 것부터 보며 w 가 그 낱말로 시작하고 더 길면 그 브랜드.
     */
    public Optional<String> dictionaryLookup(String w) {
        if (w == null || w.isEmpty()) return Optional.empty();
        String exact = dictionary.get(w);
        if (exact != null) return Optional.of(exact);
        for (Map.Entry<String, String> e : prefixWords) {
            if (w.length() > e.getKey().length() && w.startsWith(e.getKey())) return Optional.of(e.getValue());
        }
        return Optional.empty();
    }

    /** 회사 이름표. 키는 정리(clean) · 1단계 합치기를 거친 이름 그대로. 없으면 미확인(표에 없는 새 회사). */
    public Optional<CompanyLabel> companyLabel(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(companyLabels.get(name));
    }

    /** 2단계 합치기 — 정확히 같은 이름만. 포함 · 앞부분 비교는 하지 않는다(매일식품 · 해태htb 같은 다른 회사가 있어서). */
    public String merge2(String name) {
        if (name == null) return null;
        return merge2.getOrDefault(name, name);
    }

    /** 쿠팡 보정 고정값 — 이미 있던 묶음에만 걸린다. */
    public Optional<String> coupangFix(String groupKey) {
        return groupKey == null ? Optional.empty() : Optional.ofNullable(coupangFix.get(groupKey));
    }

    /** 11-12 수동 고침 — 값이 빈 문자열이면 「비움」(brand null). 표에 없으면 empty. */
    public Optional<String> fix1112(String groupKey) {
        return groupKey == null ? Optional.empty() : Optional.ofNullable(fix1112.get(groupKey));
    }

    /** 쿠팡 상품명 — 없으면 빈 문자열(「둘 다」 판정에서 제품명만 본다). */
    public String coupangName(String groupKey) {
        return groupKey == null ? "" : coupangNames.getOrDefault(groupKey, "");
    }

    public int dictionarySize()   { return dictionary.size(); }
    public int companyLabelSize() { return companyLabels.size(); }
    public int merge2Size()       { return merge2.size(); }
    public int coupangFixSize()   { return coupangFix.size(); }
    public int fix1112Size()      { return fix1112.size(); }
    public int coupangNameSize()  { return coupangNames.size(); }

    // ── 읽기 ──────────────────────────────────────────────────────────────────

    private static Map<String, String> keyed(List<Map<String, String>> rows, String keyCol, String valueCol, boolean keepEmpty) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> r : rows) {
            String k = r.get(keyCol).trim();
            String v = r.get(valueCol).trim();
            if (k.isEmpty() || (!keepEmpty && v.isEmpty())) continue;
            out.putIfAbsent(k, v);
        }
        return Map.copyOf(out);
    }

    /** CSV → 헤더 이름 키 맵 목록. required 인데 없거나 필요한 헤더가 없으면 IllegalStateException. 선택 파일이 없으면 빈 목록. */
    static List<Map<String, String>> read(String path, boolean required, String... columns) {
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) {
            if (required) throw new IllegalStateException("[BRAND] 조회표가 없습니다: " + path);
            log.warn("[BRAND] 선택 조회표가 없어 빈 표로 둡니다: {}", path);
            return List.of();
        }
        List<CsvReader.Record> records;
        try (Reader r = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8)) {
            records = CsvReader.read(r);
        } catch (IOException e) {
            throw new IllegalStateException("[BRAND] 조회표를 읽을 수 없습니다: " + path, e);
        }
        if (records.isEmpty()) {
            if (required) throw new IllegalStateException("[BRAND] 조회표가 비었습니다: " + path);
            return List.of();
        }
        List<String> header = records.get(0).fields().stream().map(String::trim).toList();
        for (String c : columns) {
            if (!header.contains(c)) throw new IllegalStateException("[BRAND] " + path + " 에 「" + c + "」 열이 없습니다. 헤더: " + header);
        }
        List<Map<String, String>> out = new ArrayList<>(records.size() - 1);
        for (CsvReader.Record rec : records.subList(1, records.size())) {
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), rec.get(i));
            out.add(row);
        }
        return out;
    }
}