package com.example.nutriuniv.domain.home.service;

import com.example.nutriuniv.domain.body.service.KcalBandResolver;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.calc.EerBand;
import com.example.nutriuniv.domain.history.service.ViewHistoryService;
import com.example.nutriuniv.domain.home.dto.HomeResponse;
import com.example.nutriuniv.domain.product.enums.Seller;
import com.example.nutriuniv.domain.product.repository.ProductListQuery;
import com.example.nutriuniv.domain.ranking.dto.RankingDtos;
import com.example.nutriuniv.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 홈 (v3 SRCH_006). 목표는 요청으로만 받는다(없으면 감량) — 등급 분포는 두 목표 모두 내려 준다. */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final KcalBandResolver kcalBandResolver;
    private final OwnerResolver ownerResolver;
    private final ViewHistoryService viewHistoryService;
    private final AnalysisRequestEventRepository analysisRequestEventRepository;
    private final RankingService rankingService;
    private final ProductListQuery listQuery;

    @Transactional(readOnly = true)
    public HomeResponse home(String goalParam, Integer kcalBand, Actor actor) {
        GoalType goal = goalParam == null || goalParam.isBlank() ? GoalType.DEFAULT : GoalType.from(goalParam);
        EerBand band = kcalBandResolver.resolve(actor, kcalBand);
        Owner owner = ownerResolver.resolveOrNull(actor);

        ViewHistoryService.ViewedStats stats = viewHistoryService.stats(owner, band);
        List<HomeResponse.GoalDistribution> goals = new ArrayList<>();
        for (GoalType g : GoalType.values()) {
            Map<String, Long> d = stats.distribution().get(g);
            goals.add(new HomeResponse.GoalDistribution(g.name(), g.label(), d,
                    d.getOrDefault("A", 0L) + d.getOrDefault("B", 0L)));
        }

        long requests = owner == null ? 0
                : owner.isUser() ? analysisRequestEventRepository.countDistinctRequestsByUserId(owner.userId())
                : analysisRequestEventRepository.countDistinctRequestsByAnonymousId(owner.anonymousId());

        List<RankingDtos.Major> majors = rankingService.categories().majors().stream()
                .filter(m -> !m.chips().isEmpty()).toList();

        List<RankingDtos.SellerCount> sellers = new ArrayList<>();
        for (Map.Entry<Seller, Long> e : listQuery.sellerCounts(ProductListQuery.Scope.all(), true).entrySet()) {
            sellers.add(new RankingDtos.SellerCount(e.getKey().name(), e.getKey().label(), e.getValue(), e.getKey().available()));
        }
        return new HomeResponse(goal.name(), band.kcal(), stats.viewedCount(), goals, requests, majors, sellers);
    }
}
