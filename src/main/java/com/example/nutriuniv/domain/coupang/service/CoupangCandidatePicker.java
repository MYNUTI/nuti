package com.example.nutriuniv.domain.coupang.service;

import com.example.nutriuniv.domain.coupang.dto.CoupangProductData;
import com.example.nutriuniv.domain.link.util.PackSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 쿠팡 검색 결과에서 연결할 상품 고르기 (DATA_006 · 11-24). 순수 함수 — 코드 규칙으로만(AI 판정 없음).
 * <ol>
 *   <li>이름 유사도(아래 점수)가 {@link #SCORE_THRESHOLD} 이상인 후보만 본다</li>
 *   <li>그 가운데 상품명의 1개 용량(g · mL)과 개수가 우리 제품과 같은 후보(SAME)를 먼저 — 같은 용량으로 연결</li>
 *   <li>없으면 양쪽 다 적혀 있는데 다른 후보(OTHER) — 다른 용량으로 연결(묶음 수 · 1개당 값)</li>
 *   <li>그것도 없으면(한쪽이라도 용량이나 개수가 없음) 자동으로 잇지 않고 운영자 확인(REVIEW)</li>
 *   <li>같은 판정끼리는 이름 점수가 높은 것, 같으면 쿠팡 검색 순서가 앞인 것</li>
 * </ol>
 * 이름 점수(항상 원본 키워드 기준): 100 정규화 후 키워드 전체 포함 · 80 모든 토큰 포함 · 70 2-gram 75% 이상 ·
 * 60 토큰 75% 이상 · 40 토큰 50% 이상 · 0 없음.
 */
public final class CoupangCandidatePicker {

    public static final int SCORE_THRESHOLD = 60;

    public enum Decision { SAME, OTHER, REVIEW, NONE }

    public record Pick(Decision decision, CoupangProductData data, int score, String rule) {
        public static final Pick NONE = new Pick(Decision.NONE, null, 0, "none");
    }

    private CoupangCandidatePicker() {}

    public static Pick pick(List<CoupangProductData> candidates, String productName) {
        if (candidates == null || candidates.isEmpty()) return Pick.NONE;
        Pick same = null, other = null, review = null;
        for (CoupangProductData c : candidates) {
            if (c == null || c.getProductName() == null) continue;
            Scored s = score(c.getProductName(), productName);
            if (s.score() < SCORE_THRESHOLD) continue;
            Decision d = switch (PackSize.compare(productName, c.getProductName())) {
                case SAME -> Decision.SAME;
                case OTHER -> Decision.OTHER;
                case UNKNOWN -> Decision.REVIEW;
            };
            Pick p = new Pick(d, c, s.score(), s.rule());
            switch (d) {
                case SAME -> same = better(same, p);
                case OTHER -> other = better(other, p);
                default -> review = better(review, p);
            }
        }
        if (same != null) return same;
        if (other != null) return other;
        if (review != null) return review;
        return Pick.NONE;
    }

    /** 이름 점수가 가장 높은 후보의 점수 — 로그용(문턱을 못 넘었을 때 얼마였는지). */
    public static int bestScore(List<CoupangProductData> candidates, String productName) {
        if (candidates == null) return 0;
        return candidates.stream().filter(c -> c != null && c.getProductName() != null)
                .mapToInt(c -> score(c.getProductName(), productName).score()).max().orElse(0);
    }

    private static Pick better(Pick current, Pick candidate) {
        return current == null || candidate.score() > current.score() ? candidate : current;
    }

    record Scored(int score, String rule) {}

    static Scored score(String candidateName, String keyword) {
        String normalizedProduct = normalize(candidateName);
        String normalizedKeyword = normalize(keyword);

        if (!normalizedKeyword.isEmpty() && normalizedProduct.contains(normalizedKeyword)) {
            return new Scored(100, "normalized-contains");
        }
        List<String> tokens = tokenize(keyword);
        if (tokens.isEmpty()) return new Scored(0, "no-match");

        long matched = tokens.stream().filter(t -> normalizedProduct.contains(normalize(t))).count();
        if (matched == tokens.size()) return new Scored(80, "all-tokens");

        // 한국어 합성어 · 띄어쓰기 변형(들깨감자탕 ↔ 들깨순살감자탕). 너무 짧으면 우연히 맞으므로 4자 이상만
        if (normalizedKeyword.length() >= 4 && bigramOverlapRatio(normalizedKeyword, normalizedProduct) >= 0.75) {
            return new Scored(70, "ngram-overlap");
        }
        if (tokens.size() >= 2) {
            double ratio = (double) matched / tokens.size();
            if (ratio >= 0.75) return new Scored(60, "most-tokens");
            if (ratio >= 0.5) return new Scored(40, "half-tokens");
        }
        return new Scored(0, "no-match");
    }

    /** 키워드 단축 변형: 원본 → (토큰 3개 이상) 마지막 토큰 제거 → (토큰 2개 이상) 첫 토큰(브랜드)만. */
    public static List<String> keywordVariants(String original) {
        List<String> tokens = tokenize(original);
        List<String> variants = new ArrayList<>();
        variants.add(original);
        if (tokens.size() >= 3) variants.add(String.join(" ", tokens.subList(0, tokens.size() - 1)));
        if (tokens.size() >= 2) variants.add(tokens.get(0));
        return variants;
    }

    private static double bigramOverlapRatio(String keyword, String text) {
        int len = keyword.length();
        if (len < 2) return 0.0;
        int total = len - 1;
        int hit = 0;
        for (int i = 0; i < total; i++) {
            if (text.contains(keyword.substring(i, i + 2))) hit++;
        }
        return (double) hit / total;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[\\s\\[\\]()\\-_,/]", "");
    }

    private static List<String> tokenize(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.trim().split("\\s+")).filter(t -> !t.isBlank()).collect(Collectors.toList());
    }
}
