package com.example.nutriuniv.domain.link.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 붙여넣은 링크 정규화 (API 명세 /links/resolve — 「저장 전 utm_·제휴코드 제거 / 동일 url 24시간 캐시」). 순수 함수.
 * <ul>
 *   <li>http(s) 만, 호스트 필수. 스킴이 없으면 https 로 본다 (「www.coupang.com/vp/products/1」 붙여넣기)</li>
 *   <li>sourceType — 쿠팡 호스트(coupang.com 하위·coupa.ng 단축)면 COUPANG, 그 외 UNKNOWN(외부 조회 안 함 → matched null → 검색 폴백)</li>
 *   <li>쿠팡 상품 URL 은 /products/{id} 의 상품 ID 를 뽑고, canonical 을 https://www.coupang.com/vp/products/{id}(+itemId·vendorItemId) 로 고정</li>
 *   <li>그 외는 utm_*·추적 파라미터·fragment 를 떼고 소문자 호스트로</li>
 * </ul>
 */
public final class LinkUrlNormalizer {

    public enum SourceType { COUPANG, UNKNOWN }

    /**
     * @param uri              원본(스킴 보정 후) URI — 외부 조회에 쓴다
     * @param canonical        저장·캐시 키
     * @param sourceType       COUPANG | UNKNOWN
     * @param coupangProductId 쿠팡 상품 ID (URL 에 있을 때만)
     * @param shortLink        coupa.ng·link.coupang.com 단축 링크 — 리다이렉트를 따라가야 상품 URL 이 나온다
     */
    public record Parsed(URI uri, String canonical, SourceType sourceType, String coupangProductId, boolean shortLink) {}

    public static final int MAX_URL_LENGTH = 2000;

    private static final Pattern COUPANG_PRODUCT = Pattern.compile("/products/(\\d+)");
    private static final Set<String> KEEP_COUPANG_PARAMS = Set.of("itemid", "vendoritemid");
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "src", "spec", "addtag", "ctag", "lptag", "itime", "pagetype", "pagevalue", "wpcid", "wref", "wtime", "redirect",
            "traceid", "mcid", "placementid", "clickbeacon", "campaignid", "contentcategory", "imgsize", "pageid", "deviceid",
            "token", "contenttype", "subid", "impressionid", "campaigntype", "puidtype", "contentkeyword", "subparam",
            "isaddedcart", "rank", "searchid", "sourcetype", "fbclid", "gclid", "igshid", "ttclid", "sharedid", "affid",
            "aff_id", "partner", "ref", "n_media", "n_query", "n_rank", "n_ad_group", "n_ad", "n_keyword_id", "n_keyword", "n_campaign_type");

    private LinkUrlNormalizer() {}

    /** @throws IllegalArgumentException 형식 오류(호출자가 400 으로 바꾼다) */
    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("url이 비어 있습니다.");
        String s = raw.trim();
        if (s.length() > MAX_URL_LENGTH) throw new IllegalArgumentException("url이 너무 깁니다.");
        if (!s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            s = "https://" + s;
        }
        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url 형식이 올바르지 않습니다.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("http·https 링크만 지원합니다.");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("url에 호스트가 없습니다.");
        host = host.toLowerCase();

        boolean coupang = isCoupangHost(host);
        boolean shortLink = coupang && (host.equals("coupa.ng") || host.equals("link.coupang.com"));
        String productId = null;
        if (coupang && uri.getPath() != null) {
            Matcher m = COUPANG_PRODUCT.matcher(uri.getPath());
            if (m.find()) productId = m.group(1);
        }

        String canonical = productId != null
                ? coupangCanonical(productId, uri.getRawQuery())
                : genericCanonical(scheme, host, uri);
        return new Parsed(uri, canonical, coupang ? SourceType.COUPANG : SourceType.UNKNOWN, productId, shortLink);
    }

    /** 쿠팡 호스트인가 — coupang.com 과 그 하위(www·m·link), 단축 coupa.ng. 외부 조회는 이 호스트로만 나간다(SSRF 방지). */
    public static boolean isCoupangHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        return h.equals("coupang.com") || h.endsWith(".coupang.com") || h.equals("coupa.ng");
    }

    public static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────────

    private static String coupangCanonical(String productId, String rawQuery) {
        StringBuilder sb = new StringBuilder("https://www.coupang.com/vp/products/").append(productId);
        List<String> kept = new ArrayList<>();
        for (String[] kv : params(rawQuery)) {
            if (KEEP_COUPANG_PARAMS.contains(kv[0].toLowerCase())) kept.add(kv[0] + "=" + kv[1]);
        }
        kept.sort(String.CASE_INSENSITIVE_ORDER);
        if (!kept.isEmpty()) sb.append('?').append(String.join("&", kept));
        return sb.toString();
    }

    private static String genericCanonical(String scheme, String host, URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (uri.getPort() != -1) sb.append(':').append(uri.getPort());
        sb.append(path);
        List<String> kept = new ArrayList<>();
        for (String[] kv : params(uri.getRawQuery())) {
            String k = kv[0].toLowerCase();
            if (k.startsWith("utm_") || TRACKING_PARAMS.contains(k)) continue;
            kept.add(kv[1].isEmpty() ? kv[0] : kv[0] + "=" + kv[1]);
        }
        if (!kept.isEmpty()) sb.append('?').append(String.join("&", kept));
        return sb.toString();
    }

    private static List<String[]> params(String rawQuery) {
        List<String[]> out = new ArrayList<>();
        if (rawQuery == null || rawQuery.isEmpty()) return out;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            if (!k.isEmpty()) out.add(new String[]{k, v});
        }
        return out;
    }
}
