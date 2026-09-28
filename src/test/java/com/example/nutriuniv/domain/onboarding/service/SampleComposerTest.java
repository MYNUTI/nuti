package com.example.nutriuniv.domain.onboarding.service;

import com.example.nutriuniv.domain.onboarding.repository.SamplePickerRepository.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 기능명세서 2.2 — A·D 각 1개 필수, 큐레이션 우선, 모자라면 조회수 상위, A/D 없으면 가장 먼 두 등급 + 경고. */
class SampleComposerTest {

    private static Sample s(long id, String grade, boolean curated) {
        return new Sample(id, "제품" + id, null, grade, curated);
    }

    @Test
    void A와_D가_들어가고_큐레이션_다음_인기순으로_채운다() {
        List<Sample> curated  = List.of(s(1, "B", true), s(2, "A", true), s(3, "C", true));
        List<Sample> popularA = List.of(s(10, "A", false));
        List<Sample> popularD = List.of(s(11, "D", false));
        List<Sample> popular  = List.of(s(20, "E", false), s(21, "B", false), s(22, "C", false));

        SampleComposer.Result r = SampleComposer.compose(curated, popularA, popularD, popular, 6);

        List<Long> ids = r.items().stream().map(Sample::productId).toList();
        assertEquals(List.of(2L, 11L, 1L, 3L, 20L, 21L), ids);     // 큐레이션 A → 인기 D → 큐레이션 나머지 → 인기
        assertTrue(r.warnings().isEmpty());
        assertEquals(6, r.items().size());
    }

    @Test
    void 못_채우면_있는_만큼_A_D는_유지() {
        SampleComposer.Result r = SampleComposer.compose(List.of(), List.of(s(1, "A", false)), List.of(s(2, "D", false)), List.of(), 6);
        assertEquals(List.of(1L, 2L), r.items().stream().map(Sample::productId).toList());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void A가_없으면_가장_먼_두_등급으로_대체하고_경고() {
        List<Sample> popular = List.of(s(1, "B", false), s(2, "E", false), s(3, "C", false));
        SampleComposer.Result r = SampleComposer.compose(List.of(), List.of(), List.of(), popular, 6);

        List<Long> ids = r.items().stream().map(Sample::productId).toList();
        assertEquals(1L, ids.get(0));       // 가장 좋은 B
        assertEquals(2L, ids.get(1));       // 가장 나쁜 E
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("B·E"));
    }

    @Test
    void 분석_완료_제품이_하나도_없으면_빈_목록과_경고() {
        SampleComposer.Result r = SampleComposer.compose(List.of(), List.of(), List.of(), List.of(), 6);
        assertTrue(r.items().isEmpty());
        assertEquals(1, r.warnings().size());
    }

    @Test
    void 중복_제품은_한_번만() {
        Sample a = s(1, "A", true);
        SampleComposer.Result r = SampleComposer.compose(List.of(a), List.of(a), List.of(s(2, "D", false)), List.of(a, s(3, "C", false)), 6);
        assertEquals(List.of(1L, 2L, 3L), r.items().stream().map(Sample::productId).toList());
    }
}
