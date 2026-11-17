package com.example.nutriuniv.domain.publicdata.compute;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 묶음 구성 (brand.md 조회표 3) — 영양성분DB 행을 묶음(group_key)으로 나누고 묶음마다 대표 행을 정한다. 순수 함수 — BrandGroupingTest 로 고정.
 * <ul>
 *   <li>이미 묶음이 정해진 food_cd 는 바꾸지 않는다. 매번 다시 묶으면 제품명이 조금 바뀔 때 다른 묶음으로 옮겨 가 묶음 키에 걸린 쿠팡 보정 · 11-12 고침이 풀린다</li>
 *   <li>새 food_cd 는 제품명 · 제조사 · 총내용량(FOOD_NM_KR, MAKER_NM, Z10500)이 같은 기존 묶음에 넣는다. 없으면 새 묶음 — 키는 세 값의 sha256 앞 16자리</li>
 *   <li>대표 행: 저장된 대표가 지금도 있으면 그 행, 없으면 묶음에서 가장 작은 food_cd</li>
 * </ul>
 */
public final class BrandGrouping {

    /** 저장된 묶음 한 줄. */
    public record Stored(String groupKey, boolean representative) {}

    /** 묶을 행 하나. */
    public record Member(String foodCd, String name, String maker, String weight) {}

    /** 새로 정한 묶음 한 줄 — 저장할 것. */
    public record Assigned(String foodCd, String groupKey, boolean representative) {}

    public record Result(Map<String, String> groupOf, Map<String, String> representativeOf, List<Assigned> assigned) {}

    private BrandGrouping() {}

    public static Result assign(Collection<Member> members, Map<String, Stored> stored) {
        Map<String, String> groupOf = new HashMap<>();
        Map<String, String> tripleToGroup = new HashMap<>();
        Set<String> usedKeys = new HashSet<>();
        for (Stored s : stored.values()) usedKeys.add(s.groupKey());

        List<Member> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(Member::foodCd));

        // 1) 이미 정해진 행 — 세 값 → 묶음 지도를 만든다(먼저 본 것)
        for (Member m : sorted) {
            Stored s = stored.get(m.foodCd());
            if (s == null) continue;
            groupOf.put(m.foodCd(), s.groupKey());
            tripleToGroup.putIfAbsent(triple(m), s.groupKey());
        }

        // 2) 새 행 — 같은 세 값의 묶음에 넣거나 새 묶음
        List<Assigned> assigned = new ArrayList<>();
        Set<String> newGroups = new HashSet<>();
        for (Member m : sorted) {
            if (groupOf.containsKey(m.foodCd())) continue;
            String t = triple(m);
            String g = tripleToGroup.get(t);
            if (g == null) {
                g = newKey(t, usedKeys);
                usedKeys.add(g);
                tripleToGroup.put(t, g);
                newGroups.add(g);
            }
            groupOf.put(m.foodCd(), g);
            // 새 묶음의 첫 행(가장 작은 food_cd)이 대표 — 정렬해서 돌므로 처음 넣는 행
            boolean rep = newGroups.remove(g);
            assigned.add(new Assigned(m.foodCd(), g, rep));
        }

        // 3) 대표 행
        Map<String, String> repOf = new HashMap<>();
        Map<String, String> smallest = new HashMap<>();
        Map<String, Boolean> newRep = new HashMap<>();
        for (Assigned a : assigned) if (a.representative()) newRep.put(a.foodCd(), true);
        for (Member m : sorted) {
            String g = groupOf.get(m.foodCd());
            smallest.putIfAbsent(g, m.foodCd());
            Stored s = stored.get(m.foodCd());
            boolean isRep = (s != null && s.representative()) || newRep.containsKey(m.foodCd());
            if (isRep) repOf.putIfAbsent(g, m.foodCd());
        }
        for (Map.Entry<String, String> e : smallest.entrySet()) repOf.putIfAbsent(e.getKey(), e.getValue());

        return new Result(groupOf, repOf, assigned);
    }

    static String triple(Member m) {
        return ComputeRules.norm(m.name()) + "\u001f" + ComputeRules.norm(m.maker()) + "\u001f" + ComputeRules.norm(m.weight());
    }

    /** sha256 앞 16자리. 이미 쓰는 키와 겹치면 뒤에 번호를 붙여 다시. */
    static String newKey(String triple, Set<String> used) {
        for (int i = 0; ; i++) {
            String k = sha256(i == 0 ? triple : triple + "#" + i).substring(0, 16);
            if (!used.contains(k)) return k;
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}