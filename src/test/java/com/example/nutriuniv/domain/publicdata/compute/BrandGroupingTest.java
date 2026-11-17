package com.example.nutriuniv.domain.publicdata.compute;

import com.example.nutriuniv.domain.publicdata.compute.BrandGrouping.Member;
import com.example.nutriuniv.domain.publicdata.compute.BrandGrouping.Stored;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrandGroupingTest {

    @Test
    void 이미_정한_묶음은_바꾸지_않는다() {
        // D1 은 이름이 바뀌었어도 저장된 묶음 그대로
        var r = BrandGrouping.assign(
                List.of(new Member("D1", "새 이름", "회사", "100g")),
                Map.of("D1", new Stored("aaaaaaaaaaaaaaaa", true)));
        assertThat(r.groupOf().get("D1")).isEqualTo("aaaaaaaaaaaaaaaa");
        assertThat(r.assigned()).isEmpty();
    }

    @Test
    void 새_행은_세_값이_같은_기존_묶음에_들어간다() {
        var r = BrandGrouping.assign(
                List.of(new Member("D1", "초코과자", "(주)회사", "100g"),
                        new Member("D9", " 초코과자 ", "(주)회사", "100g")),     // 앞뒤 공백만 다름
                Map.of("D1", new Stored("aaaaaaaaaaaaaaaa", true)));
        assertThat(r.groupOf().get("D9")).isEqualTo("aaaaaaaaaaaaaaaa");
        assertThat(r.assigned()).hasSize(1);
        assertThat(r.assigned().get(0).representative()).isFalse();
        assertThat(r.representativeOf().get("aaaaaaaaaaaaaaaa")).isEqualTo("D1");
    }

    @Test
    void 없으면_새_묶음_대표는_가장_작은_food_cd() {
        var r = BrandGrouping.assign(
                List.of(new Member("D5", "우유", "회사", "200mL"),
                        new Member("D3", "우유", "회사", "200mL"),
                        new Member("D4", "우유", "회사", "1000mL")),
                Map.of());
        String g = r.groupOf().get("D3");
        assertThat(g).hasSize(16);
        assertThat(r.groupOf().get("D5")).isEqualTo(g);
        assertThat(r.groupOf().get("D4")).isNotEqualTo(g);                   // 용량이 다르면 다른 묶음
        assertThat(r.representativeOf().get(g)).isEqualTo("D3");
        assertThat(r.assigned()).hasSize(3);
    }

    @Test
    void 같은_입력이면_같은_키() {
        var a = BrandGrouping.assign(List.of(new Member("D1", "우유", "회사", "200mL")), Map.of());
        var b = BrandGrouping.assign(List.of(new Member("D7", "우유", "회사", "200mL")), Map.of());
        assertThat(a.groupOf().get("D1")).isEqualTo(b.groupOf().get("D7"));
    }

    @Test
    void 저장된_대표가_사라지면_가장_작은_food_cd() {
        var r = BrandGrouping.assign(
                List.of(new Member("D8", "우유", "회사", "200mL"), new Member("D6", "우유", "회사", "200mL")),
                Map.of("D8", new Stored("bbbbbbbbbbbbbbbb", false), "D6", new Stored("bbbbbbbbbbbbbbbb", false)));
        assertThat(r.representativeOf().get("bbbbbbbbbbbbbbbb")).isEqualTo("D6");
    }
}