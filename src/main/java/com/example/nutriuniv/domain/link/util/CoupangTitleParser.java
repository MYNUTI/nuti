package com.example.nutriuniv.domain.link.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 쿠팡 상품 페이지·상품명 정리 (링크 인식·구매 링크 EXACT 판정). 순수 함수.
 * <ul>
 *   <li>extractTitle — HTML 의 og:title(속성 순서 무관) → &lt;title&gt; 순으로 제목을 뽑는다</li>
 *   <li>clean — 「- 쿠팡!」 같은 사이트 접미와 「, 700g, 1개」 같은 수량·용량 토막을 떼어 제품명만 남긴다</li>
 * </ul>
 */
public final class CoupangTitleParser {

    private static final Pattern OG_TITLE_PROP_FIRST = Pattern.compile(
            "<meta[^>]+property\\s*=\\s*[\"']og:title[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_TITLE_CONTENT_FIRST = Pattern.compile(
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*property\\s*=\\s*[\"']og:title[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SITE_SUFFIX = Pattern.compile("\\s*[-|:]?\\s*쿠팡\\s*!?\\s*$");
    /** 「700g」「1.5L」「2개」「10개입」「3봉」「1박스」「39g x 12개」 같은 수량·용량 토막. 단위는 「개입」처럼 겹쳐 붙을 수 있다. */
    private static final Pattern QUANTITY_PART = Pattern.compile(
            "^\\s*\\d+(?:[.,]\\d+)?\\s*(?:g|kg|mg|ml|l|oz|개|입|봉|팩|병|캔|박스|box|set|세트|매|정|포|구|인분|p|pcs|ea)+(?:\\s*[x×*]\\s*\\d+\\s*\\S*)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private CoupangTitleParser() {}

    public static Optional<String> extractTitle(String html) {
        if (html == null || html.isEmpty()) return Optional.empty();
        for (Pattern p : List.of(OG_TITLE_PROP_FIRST, OG_TITLE_CONTENT_FIRST, TITLE_TAG)) {
            Matcher m = p.matcher(html);
            if (m.find()) {
                String t = unescape(m.group(1)).trim();
                if (!t.isEmpty()) return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** 사이트 접미·수량 토막 제거. 결과가 비면 원문 trim. */
    public static String clean(String title) {
        if (title == null) return "";
        String t = SITE_SUFFIX.matcher(title.trim()).replaceAll("").trim();
        List<String> kept = new ArrayList<>();
        for (String part : t.split(",")) {
            String p = part.trim();
            if (p.isEmpty() || QUANTITY_PART.matcher(p).matches()) continue;
            kept.add(p);
        }
        String out = String.join(", ", kept).trim();
        return out.isEmpty() ? t : out;
    }

    static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }
}
