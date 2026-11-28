package com.example.nutriuniv.domain.ranking.service;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.example.nutriuniv.domain.product.enums.ListSort;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos.Item;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SRCH_007 공동 순위 (11-27) — 「점수 높은 순」에서 보이는 점수가 같으면 같은 번호, 다음 번호는 건너뛴다. */
class RankingSharedRankTest {

    private static ProductCard card(long id, String score) {
        return new ProductCard(id, "p" + id, null, null, "ANALYZED", null,
                List.of(new ProductCard.GoalGrade(GoalType.WEIGHT_LOSS.name(), new BigDecimal(score), "A", "매우 높음")),
                List.of(), List.of(), null, null, false);
    }

    private static List<Integer> ranks(List<Item> items) {
        return items.stream().map(Item::rank).toList();
    }

    @Test
    void 같은_점수는_같은_순위_다음_번호는_건너뛴다() {
        List<ProductCard> cards = List.of(card(1, "90.1"), card(2, "90.1"), card(3, "88.0"), card(4, "88.0"), card(5, "70.0"));
        assertEquals(List.of(1, 1, 3, 3, 5), ranks(RankingService.ranked(cards, 0, ListSort.SCORE, GoalType.WEIGHT_LOSS, () -> 0L)));
    }

    @Test
    void 다음_쪽_첫_제품은_앞_쪽과_공동_순위를_잇는다() {
        // 앞 쪽(20개)의 마지막 두 개와 점수가 같다 → 더 높은 제품 18개 → 19위
        List<ProductCard> cards = List.of(card(21, "60.0"), card(22, "59.0"));
        assertEquals(List.of(19, 22), ranks(RankingService.ranked(cards, 20, ListSort.SCORE, GoalType.WEIGHT_LOSS, () -> 18L)));
    }

    @Test
    void 다른_정렬은_차례대로() {
        List<ProductCard> cards = List.of(card(1, "90.1"), card(2, "90.1"));
        assertEquals(List.of(11, 12), ranks(RankingService.ranked(cards, 10, ListSort.PROTEIN_HIGH, GoalType.WEIGHT_LOSS, () -> 0L)));
    }
}
