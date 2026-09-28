package com.example.nutriuniv.domain.recommendation.service;

import com.example.nutriuniv.domain.saved.repository.SavedProductRepository;
import com.example.nutriuniv.domain.pns.service.PnsLookupService;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.recommendation.dto.RecommendationResponse;
import com.example.nutriuniv.domain.recommendation.dto.RecommendationResponse.RecommendationItem;
import com.example.nutriuniv.domain.recommendation.entity.RecommendationCache;
import com.example.nutriuniv.domain.recommendation.repository.ProductVectorByDietRepository;
import com.example.nutriuniv.domain.recommendation.repository.RecommendationCacheRepository;
import com.example.nutriuniv.domain.user.entity.UserNutrition;
import com.example.nutriuniv.domain.user.repository.UserNutritionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 20;

    // diet_purpose별 이상적인 영양 프로필 벡터 (10차원)
    private static final Map<String, String> TARGET_VECTORS = Map.of(
            "DIET",      "[0.1,0.3,0.1,0.5,0.1,0.1,0.0,0.2,0.2,0.8]",
            "BULK",      "[0.8,0.7,0.3,0.9,0.3,0.2,0.0,0.2,0.4,0.3]",
            "LEAN_MASS", "[0.5,0.3,0.2,0.9,0.2,0.1,0.0,0.2,0.3,0.5]",
            "HEALTHY",   "[0.3,0.4,0.1,0.5,0.2,0.1,0.0,0.1,0.1,0.7]",
            "OTHER",     "[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]"
    );

    private final RecommendationCacheRepository recommendationCacheRepository;
    private final ProductVectorByDietRepository productVectorByDietRepository;
    private final ProductRepository productRepository;
    private final UserNutritionRepository userNutritionRepository;
    private final SavedProductRepository savedProductRepository;
    private final PnsLookupService pnsLookupService;

    public RecommendationResponse getRecommendations(Long userId) {
        int eerBand = pnsLookupService.resolveEerBand(userId);
        String goal = pnsLookupService.resolveGoal(userId);

        // 1단계: CF — recommendation_cache에 결과가 있으면 반환
        List<RecommendationCache> cfCache = recommendationCacheRepository
                .findByUserIdOrderByScoreDesc(userId);
        if (!cfCache.isEmpty()) {
            return buildCfResponse(cfCache, userId, eerBand, goal);
        }

        // 2단계: 콘텐츠 기반 — diet_purpose로 영양소 벡터 유사도 추천
        Optional<UserNutrition> nutrition = userNutritionRepository.findByUserId(userId);
        if (nutrition.isPresent()) {
            String dietPurpose = nutrition.get().getDietPurpose();
            String targetVector = resolveTargetVector(dietPurpose);
            List<Long> productIds = productVectorByDietRepository
                    .findTopProductIdsByDietPurpose(dietPurpose, targetVector, DEFAULT_LIMIT);
            if (!productIds.isEmpty()) {
                return buildContentResponse(productIds, userId, eerBand, goal);
            }
        }

        // 3단계: 인기순 폴백
        return buildPopularResponse(userId, eerBand, goal);
    }

    /**
     * diet_purpose에 해당하는 타겟 벡터 문자열 반환.
     * ProductService의 RECOMMENDED 정렬(findAllOrderByPreference)에서도 재사용됨 — 로직 중복 방지용 public 메서드.
     */
    public String resolveTargetVector(String dietPurpose) {
        return TARGET_VECTORS.getOrDefault(dietPurpose, TARGET_VECTORS.get("OTHER"));
    }

    // ── CF 응답 ───────────────────────────────────────────────────────────────────

    private RecommendationResponse buildCfResponse(List<RecommendationCache> cfCache,
                                                   Long userId, int eerBand, String goal) {
        Map<Long, Double> scoreByProductId = cfCache.stream()
                .collect(Collectors.toMap(
                        RecommendationCache::getProductId,
                        c -> c.getScore().doubleValue()
                ));

        List<Long> productIds = cfCache.stream()
                .map(RecommendationCache::getProductId)
                .collect(Collectors.toList());

        List<Product> products = productRepository.findByIdInAndIsActiveTrue(productIds);
        Set<Long> favoritedIds = getFavoritedIds(userId);
        Map<Long, String> gradeMap = pnsLookupService.lookupGrades(productIds, eerBand, goal);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<RecommendationItem> items = productIds.stream()
                .filter(productMap::containsKey)
                .map(id -> toItem(productMap.get(id), favoritedIds, scoreByProductId.get(id), gradeMap.get(id)))
                .collect(Collectors.toList());

        return RecommendationResponse.builder()
                .type("CF")
                .items(items)
                .build();
    }

    // ── 콘텐츠 기반 응답 ──────────────────────────────────────────────────────────

    private RecommendationResponse buildContentResponse(List<Long> productIds,
                                                        Long userId, int eerBand, String goal) {
        List<Product> products = productRepository.findByIdInAndIsActiveTrue(productIds);
        Set<Long> favoritedIds = getFavoritedIds(userId);
        Map<Long, String> gradeMap = pnsLookupService.lookupGrades(productIds, eerBand, goal);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<RecommendationItem> items = productIds.stream()
                .filter(productMap::containsKey)
                .map(id -> toItem(productMap.get(id), favoritedIds, null, gradeMap.get(id)))
                .collect(Collectors.toList());

        return RecommendationResponse.builder()
                .type("CONTENT")
                .items(items)
                .build();
    }

    // ── 인기순 폴백 응답 ──────────────────────────────────────────────────────────

    private RecommendationResponse buildPopularResponse(Long userId, int eerBand, String goal) {
        List<Product> products = productRepository
                .findTopByIsActiveTrueOrderByViewCountDesc(PageRequest.of(0, DEFAULT_LIMIT));
        Set<Long> favoritedIds = getFavoritedIds(userId);
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, String> gradeMap = pnsLookupService.lookupGrades(productIds, eerBand, goal);

        List<RecommendationItem> items = products.stream()
                .map(p -> toItem(p, favoritedIds, null, gradeMap.get(p.getId())))
                .collect(Collectors.toList());

        return RecommendationResponse.builder()
                .type("POPULAR")
                .items(items)
                .build();
    }

    // ── 공통 헬퍼 ────────────────────────────────────────────────────────────────

    private Set<Long> getFavoritedIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        return savedProductRepository.findSavedProductIdsByUserId(userId);
    }

    private RecommendationItem toItem(Product p, Set<Long> favoritedIds, Double score, String grade) {
        return RecommendationItem.builder()
                .id(p.getId())
                .name(p.getName())
                .imageUrl(p.getImageUrl())
                .nutritionScore(p.getNutritionScore())
                .grade(grade)
                .isFavorited(favoritedIds.contains(p.getId()))
                .score(score)
                .brand(p.getBrand() != null
                        ? RecommendationResponse.BrandInfo.builder()
                        .id(p.getBrand().getId())
                        .name(p.getBrand().getName())
                        .build()
                        : null)
                .category(p.getCategory() != null
                        ? RecommendationResponse.CategoryInfo.builder()
                        .id(p.getCategory().getId())
                        .name(p.getCategory().getName())
                        .build()
                        : null)
                .build();
    }
}