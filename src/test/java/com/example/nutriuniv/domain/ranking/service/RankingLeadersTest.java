package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.body.service.KcalBandResolver;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.product.service.ProductCardService;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.Leader;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.LeadersResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 랭킹 첫 화면 (SRCH_007 · 11-24) — 대분류마다 열린 중분류 앞 3개(보이는 제품 많은 순)의 1위, 합산 칩 · 숨긴 「기타」 제외. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RankingLeadersTest {

    @Mock KcalBandResolver kcalBandResolver;
    @Mock CategoryRepository categoryRepository;
    @Mock ChipStatsService chipStats;
    @Mock ProductListQuery listQuery;
    @Mock ProductCardService cardService;
    @Mock OwnerResolver ownerResolver;

    @InjectMocks RankingService service;

    private final Map<Long, ChipStatsService.Stat> stats = new HashMap<>();
    /** 칩 id → 그 칩 1위 제품 id (없으면 필터에 맞는 제품 없음). */
    private final Map<Long, Long> firstPlace = new HashMap<>();
    private final List<Category> all = new ArrayList<>();
    private long nextId = 1;

    private static final Actor ACTOR = new Actor(null, null, "anon", null, null, null);

    @BeforeEach
    void setUp() {
        when(kcalBandResolver.resolve(any())).thenReturn(EerBand.DEFAULT);
        when(categoryRepository.findAllByIsActiveTrue()).thenAnswer(inv -> all);
        when(chipStats.stat(anyLong())).thenAnswer(inv -> stats.getOrDefault((Long) inv.getArgument(0), ChipStatsService.Stat.EMPTY));
        when(listQuery.top(any(), any(), any(), anyInt(), eq(1))).thenAnswer(inv -> {
            ProductListQuery.Scope scope = inv.getArgument(0);
            Long top = firstPlace.get((Long) scope.args().get(0));
            return top == null ? List.of() : List.of(top);
        });
        when(cardService.cards(anyList(), any(), any(), any(), any())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(id -> new ProductCard(id, "제품 " + id, null, null, "ANALYZED", "분석 완료",
                    List.of(), List.of(), null, null, false)).toList();
        });
    }

    @Test
    void 대분류마다_열린_중분류_앞_3개의_1위를_보이는_제품_많은_순으로() {
        Category diet = major("01", 1, false);
        var powder = chip("0101", diet, 1, 500, 310, 101L);
        chip("0102", diet, 2, 900, 600, 102L).aggregate();            // 합산 칩은 넣지 않는다
        var bar = chip("0103", diet, 3, 800, 400, 103L);           // 보이는 제품이 더 많아 앞
        chip("0104", diet, 4, 1000, 200, 104L);                         // 300개 미만 — 순위가 안 열림

        Category meal = major("02", 2, false);
        chip("0201", meal, 1, 5000, 3000, null);                        // 필터에 맞는 제품 없음 → 다음 칩이 채움
        var rice = chip("0202", meal, 2, 400, 350, 202L);
        var sandwich = chip("0203", meal, 3, 700, 650, 203L);
        var kimbap = chip("0204", meal, 4, 600, 550, 204L);
        chip("0205", meal, 5, 300, 300, 205L);                          // 넷째 — 앞 3개가 다 찼다

        Category drink = major("03", 3, false);
        chip("0301", drink, 1, 100, 50, 301L);                          // 열린 칩이 없는 대분류 → 묶음째 뺀다

        Category etc = major("99", 99, true);
        chip("9904", etc, 4, 5000, 5000, 9904L).hide();                 // 숨긴 「기타」는 어디에도 없다

        LeadersResponse r = service.leaders("WEIGHT_LOSS", ProductListQuery.Filters.NONE, ACTOR);

        assertEquals("WEIGHT_LOSS", r.goal());
        assertEquals(List.of("01", "02"), r.groups().stream().map(g -> g.majorCode()).toList());
        assertEquals(List.of(bar.getCode(), powder.getCode()), codes(r.groups().get(0).items()));
        assertEquals(List.of(sandwich.getCode(), kimbap.getCode(), rice.getCode()), codes(r.groups().get(1).items()));
        assertEquals(203L, r.groups().get(1).items().get(0).product().productId());
        assertEquals("간편식", r.groups().get(1).items().get(0).category().majorName());
        verify(listQuery, never()).top(argThat(s -> s.args().contains(9904L)), any(), any(), anyInt(), anyInt());
        verify(listQuery, never()).top(argThat(s -> s.args().contains(102L)), any(), any(), anyInt(), anyInt());
    }

    @Test
    void 열린_종류가_없으면_빈_묶음() {
        Category diet = major("01", 1, false);
        chip("0101", diet, 1, 100, 10, 101L);

        LeadersResponse r = service.leaders(null, ProductListQuery.Filters.NONE, ACTOR);

        assertEquals("WEIGHT_LOSS", r.goal());   // 목표가 없으면 감량
        assertTrue(r.groups().isEmpty());
        verifyNoInteractions(cardService);
    }

    // ── 도우미 ──

    private static List<String> codes(List<Leader> items) {
        return items.stream().map(i -> i.category().code()).toList();
    }

    private Category major(String code, int order, boolean hidden) {
        Category c = Category.seeded(code, switch (code) {
            case "01" -> "다이어트·단백질";
            case "02" -> "간편식";
            case "03" -> "음료";
            default -> "기타";
        }, null, order, hidden, false);
        ReflectionTestUtils.setField(c, "id", nextId++);
        all.add(c);
        return c;
    }

    /** 중분류 — total = 보이는 제품 수, analyzed = 분석 완료 수(300 이상이면 순위 열림), top = 그 칩 1위 제품. */
    private ChipBuilder chip(String code, Category major, int order, long total, long analyzed, Long top) {
        Category c = Category.seeded(code, "칩 " + code, major, order);
        ReflectionTestUtils.setField(c, "id", Long.parseLong(code));
        all.add(c);
        stats.put(c.getId(), new ChipStatsService.Stat(total, analyzed));
        if (top != null) firstPlace.put(c.getId(), top);
        return new ChipBuilder(c);
    }

    private record ChipBuilder(Category c) {
        ChipBuilder aggregate() {
            ReflectionTestUtils.setField(c, "aggregate", true);
            return this;
        }

        ChipBuilder hide() {
            ReflectionTestUtils.setField(c, "hidden", true);
            return this;
        }

        String getCode() {
            return c.getCode();
        }
    }
}
