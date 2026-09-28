package com.example.nutriuniv.domain.onboarding.service;

import com.example.nutriuniv.domain.grade.entity.Grade;
import com.example.nutriuniv.domain.onboarding.repository.SamplePickerRepository.Sample;

import java.util.*;

/**
 * 샘플 픽커 구성 규칙 (기능명세서 2.2) — 순수 함수.
 * <ul>
 *   <li>A등급 1개와 D등급 1개가 반드시 들어간다 (차이가 보여야 서비스가 뭔지 전달된다)</li>
 *   <li>관리자 지정 목록을 먼저 쓰고, 모자라면 조회수 상위로 채운다</li>
 *   <li>못 채워도 A·D 구성은 유지. A 나 D 가 아예 없으면 있는 것 중 가장 먼 두 등급으로 대체하고 경고를 남긴다</li>
 * </ul>
 */
public final class SampleComposer {

    private SampleComposer() {}

    public record Result(List<Sample> items, List<String> warnings) {}

    /**
     * @param curated     관리자 지정(순서대로), 분석 완료만
     * @param popularA    A등급 조회수 상위
     * @param popularD    D등급 조회수 상위
     * @param popular     등급 무관 조회수 상위 (채우기용)
     */
    public static Result compose(List<Sample> curated, List<Sample> popularA, List<Sample> popularD,
                                 List<Sample> popular, int size) {
        LinkedHashMap<Long, Sample> picked = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        Sample a = firstWithGrade(curated, Grade.A).orElseGet(() -> popularA.isEmpty() ? null : popularA.get(0));
        Sample d = firstWithGrade(curated, Grade.D).orElseGet(() -> popularD.isEmpty() ? null : popularD.get(0));

        if (a == null || d == null) {
            // 가장 먼 두 등급으로 대체
            List<Sample> pool = new ArrayList<>();
            pool.addAll(curated); pool.addAll(popularA); pool.addAll(popularD); pool.addAll(popular);
            Sample best = null, worst = null;
            for (Sample s : pool) {
                Grade g = Grade.fromString(s.grade());
                if (g == null) continue;
                if (best == null || g.ordinal() < Grade.fromString(best.grade()).ordinal()) best = s;
                if (worst == null || g.ordinal() > Grade.fromString(worst.grade()).ordinal()) worst = s;
            }
            if (best == null) {
                warnings.add("샘플로 보여줄 분석 완료 제품이 없습니다 — 제품 적재·등급 계산 상태를 확인하세요.");
            } else {
                warnings.add(String.format("A 또는 D 등급 제품이 없어 %s·%s 등급으로 대체했습니다 — 큐레이션에 A·D 를 채워주세요.",
                        best.grade(), worst.grade()));
            }
            if (a == null) a = best;
            if (d == null) d = worst != null && (a == null || worst.productId() != a.productId()) ? worst : null;
        }

        if (a != null) picked.put(a.productId(), a);
        if (d != null) picked.put(d.productId(), d);
        for (Sample s : curated) {
            if (picked.size() >= size) break;
            picked.putIfAbsent(s.productId(), s);
        }
        for (Sample s : popular) {
            if (picked.size() >= size) break;
            picked.putIfAbsent(s.productId(), s);
        }
        return new Result(new ArrayList<>(picked.values()).subList(0, Math.min(size, picked.size())), warnings);
    }

    private static Optional<Sample> firstWithGrade(List<Sample> list, Grade grade) {
        return list.stream().filter(s -> grade.name().equals(s.grade())).findFirst();
    }
}
