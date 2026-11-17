package com.example.nutriuniv.domain.home.service;

import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.common.security.Owner;
import com.example.nutriuniv.domain.analysis.repository.AnalysisRequestEventRepository;
import com.example.nutriuniv.domain.consent.service.OwnerResolver;
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

/** 홈 (v3 SRCH_006 · 11-11 대시보드를 빼서 등급 분포는 없다). */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final OwnerResolver ownerResolver;
    private final ViewHistoryService viewHistoryService;
    private final AnalysisRequestEventRepository analysisRequestEventRepository;
    private final RankingService rankingService;
    private final ProductListQuery listQuery;

    @Transactional(readOnly = true)
    public HomeResponse home(Actor actor) {
        Owner owner = ownerResolver.resolveOrNull(actor);
        long viewed = viewHistoryService.viewedCount(owner);

        long requests = owner == null ? 0
                : owner.isUser() ? analysisRequestEventRepository.countDistinctRequestsByUserId(owner.userId())
                : analysisRequestEventRepository.countDistinctRequestsByAnonymousId(owner.anonymousId());

        // 종류 격자는 대분류 15칸을 늘 보인다(순위가 열린 칩이 없어도 · 11-09)
        List<RankingDtos.Major> majors = rankingService.categories().majors();

        List<RankingDtos.SellerCount> sellers = new ArrayList<>();
        for (Map.Entry<Seller, Long> e : listQuery.sellerCounts(ProductListQuery.Scope.all(), true).entrySet()) {
            sellers.add(new RankingDtos.SellerCount(e.getKey().name(), e.getKey().label(), e.getValue(), e.getKey().available()));
        }
        return new HomeResponse(viewed, requests, majors, sellers);
    }
}
