package com.example.nutriuniv.domain.publicdata.brand;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * brand 칸 계산의 문자열 규칙 (brand.md 「함수 정의」 · 「회사 이름 정리」 · 「작은 표」). 순수 함수 — 테스트(BrandNameRulesTest)로 고정.
 * 지명 목록(표 7)만 인스턴스마다 다르다 — 시도 17개가 기본이고, 공공데이터 수집이 붙으면 I0300 · I1420 SITE_ADDR 지명을 더한다.
 */
public final class BrandNameRules {

    /** 시도 17개 — 지명 목록의 기본값. */
    public static final Set<String> PROVINCES = Set.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
            "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    /** 하위 브랜드 — 제품명 첫 낱말이 이것이면 누가 만들어도 그 이름. 키는 compact. */
    static final Map<String, String> UNIQUE = Map.ofEntries(
            Map.entry("피코크", "피코크"), Map.entry("노브랜드", "노브랜드"), Map.entry("유어스", "유어스"),
            Map.entry("헤이루", "헤이루"), Map.entry("heyroo", "헤이루"), Map.entry("세븐셀렉트", "세븐셀렉트"),
            Map.entry("오늘좋은", "오늘좋은"), Map.entry("초이스엘", "초이스엘"), Map.entry("요리하다", "요리하다"),
            Map.entry("심플러스", "심플러스"), Map.entry("simplus", "심플러스"), Map.entry("곰곰", "곰곰"),
            Map.entry("탐사", "탐사"), Map.entry("비비고", "비비고"), Map.entry("햇반", "햇반"),
            Map.entry("청정원", "청정원"), Map.entry("종가집", "종가집"), Map.entry("해찬들", "해찬들"));

    /** 하위 브랜드 — 그 회사(1단계 합치기 뒤 이름) 제품일 때만. 낱말은 compact. */
    static final Map<String, Set<String>> SUB = Map.of(
            "CJ제일제당", Set.of("비비고", "햇반", "백설", "고메", "스팸", "맛밤", "해찬들", "다담", "컵반", "쁘띠첼", "프레시안"),
            "대상", Set.of("청정원", "종가", "종가집", "안주야", "호밍스"),
            "오리온", Set.of("닥터유", "마켓오"),
            "매일유업", Set.of("상하목장"),
            "동서식품", Set.of("맥심"),
            "정식품", Set.of("베지밀"),
            "동원F&B", Set.of("양반", "리챔", "덴마크"));

    /** 1단계 합치기 — 적힌 순서대로, 처음 맞는 것에서 멈춘다. starts=true 면 compact 가 그 글자로 시작, false 면 같을 때만. */
    record Merge1(boolean starts, List<String> keys, String display) {}

    static final List<Merge1> MERGE1;
    static {
        List<Merge1> m = new ArrayList<>();
        m.add(new Merge1(true, List.of("씨제이제일제당", "cj제일제당"), "CJ제일제당"));
        m.add(new Merge1(true, List.of("롯데웰푸드", "롯데제과", "롯데푸드"), "롯데웰푸드"));
        m.add(new Merge1(true, List.of("동원에프앤비", "동원fb"), "동원F&B"));
        m.add(new Merge1(true, List.of("에치와이", "한국야쿠르트"), "hy"));
        m.add(new Merge1(true, List.of("해태에이치티비"), "해태htb"));
        m.add(new Merge1(true, List.of("에스피씨삼립"), "SPC삼립"));
        for (String name : List.of("롯데칠성음료", "오뚜기", "삼양식품", "해태아이스크림", "해태제과", "남양유업", "매일유업",
                "서울우유", "풀무원", "크라운제과", "동서식품", "코카콜라음료", "동아오츠카", "하림", "사조", "샘표", "광동제약",
                "웅진식품", "신세계푸드", "아워홈", "크리스피크림")) {
            m.add(new Merge1(true, List.of(name), name));
        }
        for (String name : List.of("농심", "팔도", "빙그레", "대상", "오리온", "일화", "정식품")) {
            m.add(new Merge1(false, List.of(name), name));
        }
        MERGE1 = List.copyOf(m);
    }

    /** 계열 5쌍 (회사, 바코드 주인) — 같은 그룹이라 남의 제품으로 보지 않는다. */
    static final List<String[]> AFFILIATES = List.of(
            new String[]{"해태아이스크림", "빙그레"}, new String[]{"샤니", "삼립"}, new String[]{"호남샤니", "삼립"},
            new String[]{"비락", "hy"}, new String[]{"남양유업", "백미당"});

    /** 꼬리 목록 (core) — 긴 것부터 맞춘다. 모두 compact 형태. */
    static final List<String> TAILS = List.of(
                    "종합식품", "식품공업", "식품산업", "식품", "푸드시스템", "푸드", "에프앤비", "엔에프", "f&b", "fnb", "에프엔비", "제과",
                    "유업", "농산", "수산", "산업", "코리아", "korea", "인터내셔날", "인터내셔널", "글로벌", "영농조합", "농협", "컴퍼니",
                    "상사", "무역", "유통", "바이오", "팜", "제약", "헬스케어", "주류", "음료", "축산", "육가공", "씨푸드", "베이커리")
            .stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();

    private static final Pattern NON_COMPACT = Pattern.compile("[^0-9a-z가-힣&+]");
    private static final Pattern LEADING_BRACKET = Pattern.compile("^[\\[(【<]([^\\])】>]{1,20})[\\])】>]");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s/·,]+");

    private static final Pattern KO_LEGAL = Pattern.compile(
            "학교법인|사회복지법인|재단법인|사단법인|\\(주\\)|㈜|주식회사|\\(유\\)|유한책임회사|유한회사|농업회사법인|영농조합법인|어업회사법인|\\(합\\)|합자회사|\\(사\\)|\\(재\\)|\\(농\\)");
    private static final Pattern PAREN_SITE = Pattern.compile("\\([^)]*(공장|센터|지점)\\)");
    private static final Pattern TRAILING_EN_PAREN = Pattern.compile("\\([A-Za-z0-9&.,' -]+\\)\\s*$");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern AND_CO_LTD = Pattern.compile("(?i)\\s*&\\s*CO\\.?\\s*,?\\s*LTD.*$");
    private static final Pattern FACTORY = Pattern.compile("(?i)\\s*\\S*\\s+FACTORY\\s*$");
    private static final Pattern GLUED_LEGAL = Pattern.compile("(?i)(?<=[A-Za-z]{4})(GMBH|SPA|BV|SAS|LTD)$");
    private static final Pattern EN_TAIL = Pattern.compile("(?i)[,\\s]+(CO\\.?\\s*,?\\s*LTD|PTY\\.?\\s*LTD|GMBH\\s*&\\s*CO\\.?\\s*KG|CORPORATION|LIMITED|LTD|INC|CORP"
            + "|S\\.A\\.S|SPRL|S\\.R\\.L|S\\.P\\.A|S\\.L|S\\.A|A\\.G|LLC|BVBA|B\\.V|KG|OY|AB|AS|NV)\\.?$");
    private static final Pattern SPACED_FACTORY = Pattern.compile("\\s+\\S*공장$");
    private static final Pattern SPACED_SITE = Pattern.compile("\\s+\\S*(지점|캠퍼스|사업장|센트럴키친|센터)$");
    private static final Pattern SPACED_STORE = Pattern.compile("\\s+\\S{2,}점$");
    private static final Pattern NUMBERED_FACTORY = Pattern.compile("(제\\d+\\S*공장|\\d+공장)$");

    // 해외 법인 꼬리 2차 (apply_v3 LEG) — 제조사 · 수입에서 온 값에만
    private static final Pattern FOREIGN_HEAD = Pattern.compile("(?i)^(SIA\\s+|PT\\.\\s*|CV\\.\\s*)");
    private static final Pattern FOREIGN_TAIL = Pattern.compile("(?i)(\\s+TBK\\b.*"
            + "|\\s*-\\s*SITE\\b.*"
            + "|\\s+JOINT\\s+STOCK(\\s+COMPANY)?"
            + "|\\s+ANONIM\\s+SIRKETI"
            + "|\\s+(SANAYI\\s+)?VE\\s+TICARET(\\s+A\\.S\\.?)?"
            + "|\\s+LIMITED\\s+SIRKETI"
            + "|\\s+(CO\\.?,?\\s+)?LIMITED"
            + "|\\s+PUBLIC\\s+COMPANY"
            + "|\\s+SDN\\.?\\s+BHD\\.?"
            + "|\\s+BHD\\.?"
            + "|\\s+PTE\\.?"
            + "|\\s+PVT\\.?"
            + "|\\s+LLP"
            + "|\\s+S\\.A\\.U\\.?"
            + "|\\s+JSC"
            + "|(?<=[A-Za-z]{4})(SRL|SPA)"
            + "|\\s+(SRL|SPA)"
            + "|(?<!&)\\s+CO\\.?)\\s*$");

    private final Set<String> placeNames;
    private final Pattern placeStore;     // 지명 + 0~6글자 + 지점/점/캠퍼스/센터/사업장
    private final Pattern placeFactory;   // 지명 + 공장

    public BrandNameRules() {
        this(PROVINCES);
    }

    public BrandNameRules(Set<String> placeNames) {
        this.placeNames = Set.copyOf(placeNames);
        String alt = this.placeNames.stream()
                .filter(p -> p.length() >= 2)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        this.placeStore = alt.isEmpty() ? null : Pattern.compile("(" + alt + ")\\S{0,6}(지점|점|캠퍼스|센터|사업장)$");
        this.placeFactory = alt.isEmpty() ? null : Pattern.compile("(" + alt + ")공장$");
    }

    public Set<String> placeNames() {
        return placeNames;
    }

    // ── 기본 함수 ────────────────────────────────────────────────────────────

    /** NFKC → 소문자 → [0-9a-z가-힣&+] 외 전부 제거. */
    public static String compact(String s) {
        if (s == null) return "";
        return NON_COMPACT.matcher(Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** 제품명 → 낱말. 맨 앞 괄호(안 글자 1~20자)는 괄호만 벗겨 낱말로, _ 는 따로 낱말로, 공백 · / · · · , 로 나눈다. */
    public static List<String> tokens(String name) {
        if (name == null) return List.of();
        String s = Normalizer.normalize(name, Normalizer.Form.NFKC).strip();
        Matcher m = LEADING_BRACKET.matcher(s);
        if (m.find()) s = m.group(1) + " " + s.substring(m.end());
        s = s.replace("_", " _ ");
        List<String> out = new ArrayList<>();
        for (String t : TOKEN_SPLIT.split(s)) if (!t.isEmpty()) out.add(t);
        return out;
    }

    /** 빈 값이거나 「제조 ○○」면 null — 마스터의 「제조 ○○」는 우리 쪽에서 null. */
    public static String nullIfMaker(String s) {
        if (s == null || s.isBlank() || s.strip().startsWith("제조 ")) return null;
        return s.strip();
    }

    /** 1단계 합치기 — 회사 이름표 키가 된다. 맞는 것이 없으면 s 그대로. */
    public static String merge1(String s) {
        String c = compact(s);
        for (Merge1 m : MERGE1) {
            for (String k : m.keys()) {
                if (m.starts() ? c.startsWith(k) : c.equals(k)) return m.display();
            }
        }
        return s;
    }

    /** compact 에서 꼬리를 끝에서 최대 3번 뗀다. 떼고 2글자 미만이 되면 멈춘다. */
    public static String core(String s) {
        String c = compact(s);
        for (int i = 0; i < 3; i++) {
            boolean cut = false;
            for (String tail : TAILS) {
                if (c.endsWith(tail) && c.length() - tail.length() >= 2) {
                    c = c.substring(0, c.length() - tail.length());
                    cut = true;
                    break;
                }
            }
            if (!cut) break;
        }
        return c;
    }

    /** 같은 회사인가 — core 가 같거나, 둘 다 2글자 이상이고 한쪽이 다른 쪽에 포함. */
    public static boolean same(String a, String b) {
        if (a == null || b == null) return false;
        String ca = core(a), cb = core(b);
        if (ca.equals(cb)) return !ca.isEmpty();
        return ca.length() >= 2 && cb.length() >= 2 && (ca.contains(cb) || cb.contains(ca));
    }

    /** 계열 5쌍 중 same(v, 회사) && same(o, 바코드 주인)인 쌍이 있는가. */
    public static boolean affiliate(String v, String owner) {
        for (String[] p : AFFILIATES) if (same(v, p[0]) && same(owner, p[1])) return true;
        return false;
    }

    /** 해외 법인 꼬리 2차 — 최대 3번, 앞뒤 공백 · 쉼표 · 점 · 하이픈 제거. 결과가 2글자 미만이면 원래 v. */
    public static String stripForeign(String v) {
        if (v == null) return null;
        String s = v;
        for (int i = 0; i < 3; i++) {
            String before = s;
            s = FOREIGN_HEAD.matcher(s).replaceFirst("");
            s = FOREIGN_TAIL.matcher(s).replaceFirst("");
            s = trimPunct(s);
            if (s.equals(before)) break;
        }
        return s.length() < 2 ? v : s;
    }

    // ── 회사 이름 정리 (clean) ────────────────────────────────────────────────

    /** brand.md 「회사 이름 정리」 1~5. 결과가 비면 원문(strip). */
    public String clean(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip();
        // 1. 앞의 M/S · A/S, 끝의 A/S. / 가 있으면 앞부분만
        s = s.replaceFirst("(?i)^(M/S|A/S)\\s*", "");
        s = s.replaceFirst("(?i)\\s*A/S$", "");
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        // 2. 한글 법인 표기 → 공백
        s = KO_LEGAL.matcher(s).replaceAll(" ");
        // 3. 괄호 속 공장 · 센터 · 지점, 끝의 영문 괄호
        s = PAREN_SITE.matcher(s).replaceAll("");
        s = TRAILING_EN_PAREN.matcher(s).replaceAll("");
        s = trimPunct(MULTI_SPACE.matcher(s).replaceAll(" "));
        // 4. 바뀌지 않을 때까지 최대 3번
        for (int i = 0; i < 3; i++) {
            String before = s;
            s = AND_CO_LTD.matcher(s).replaceFirst("");
            s = FACTORY.matcher(s).replaceFirst("");
            s = removeIfLeft(s, GLUED_LEGAL, 3);
            s = EN_TAIL.matcher(s).replaceFirst("");
            s = removeIfLeft(s, SPACED_FACTORY, 2);
            s = removeIfLeft(s, SPACED_SITE, 2);
            s = removeIfLeft(s, SPACED_STORE, 2);
            if (placeStore != null) s = removeIfLeft(s, placeStore, 2);
            s = removeIfLeft(s, NUMBERED_FACTORY, 2);
            if (placeFactory != null) s = removeIfLeft(s, placeFactory, 2);
            if (s.endsWith("공장") && s.length() > 4) s = s.substring(0, s.length() - 2);
            s = trimPunct(s);
            if (s.equals(before)) break;
        }
        // 5. 비면 원문
        return s.isEmpty() ? raw.strip() : s;
    }

    /** 맞는 부분을 지웠을 때 남는 글자가 min 이상일 때만 지운다. */
    private static String removeIfLeft(String s, Pattern p, int min) {
        Matcher m = p.matcher(s);
        if (!m.find()) return s;
        String left = trimPunct(s.substring(0, m.start()) + s.substring(m.end()));
        return left.length() >= min ? left : s;
    }

    private static String trimPunct(String s) {
        return s.replaceAll("^[\\s,.\\-]+|[\\s,.\\-]+$", "");
    }
}