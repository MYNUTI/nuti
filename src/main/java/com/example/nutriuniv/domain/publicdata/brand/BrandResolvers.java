package com.example.nutriuniv.domain.publicdata.brand;

import java.util.HashSet;
import java.util.Set;

/**
 * 지명 목록을 넣은 BrandResolver 만들기 (brand.md 조회표 7). 지명 목록은 I0300 · I1420 SITE_ADDR 로 재계산 때마다 만들어지므로
 * Spring 빈(시도 17개만 아는 기본값) 대신 이것을 쓴다.
 */
public final class BrandResolvers {

    private BrandResolvers() {}

    public static BrandNameRules rules(Set<String> extraPlaceNames) {
        Set<String> places = new HashSet<>(BrandNameRules.PROVINCES);
        for (String p : extraPlaceNames) if (p != null && p.strip().length() >= 2) places.add(p.strip());
        return new BrandNameRules(places);
    }

    public static BrandResolver withRules(BrandLookupTables tables, BrandNameRules rules) {
        return new BrandResolver(tables, rules);
    }
}