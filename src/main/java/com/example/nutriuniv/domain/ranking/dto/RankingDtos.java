package com.example.nutriuniv.domain.ranking.dto;

import com.example.nutriuniv.domain.product.dto.ProductCard;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/** 랭킹 · 필터 응답 묶음 (v3 SRCH_006 · SRCH_007 · SRCH_008). */
public final class RankingDtos {

    private RankingDtos() {}

    /** 칩 = 중분류 또는 합산 칩. open = 분석 완료 300개 이상. */
    public record Chip(String code, String name, boolean aggregate, long total, long analyzed, boolean open) {}

    public record Major(String code, String name, List<Chip> chips) {}

    /** GET /rankings/categories — 대분류 15개(늘 보임)와 순위가 열린 칩. */
    public record CategoriesResponse(
            List<Major> majors,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) {}

    public record CategoryInfo(String code, String name, String majorCode, String majorName) {}

    /** 「000개 중 000개 점수 있음, 날짜 업데이트」. */
    public record Coverage(long total, long analyzed,
                           @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) {}

    public record Item(int rank, ProductCard product) {}

    /** GET /rankings — 칩 하나의 순위. */
    public record RankingResponse(
            CategoryInfo category,
            String goal,
            int kcalBand,
            String sort,
            String sortLabel,
            Coverage coverage,
            long total,
            int page,
            int size,
            boolean hasNext,
            List<Item> items) {}

    public record Group(CategoryInfo category, Coverage coverage, List<Item> items) {}

    /** GET /rankings/overview — 대분류 전체: 열린 칩마다 필터에 맞는 상위 3개(0개인 칩은 뺀다). */
    public record OverviewResponse(String majorCode, String majorName, String goal, int kcalBand, List<Group> groups) {}

    public record NutrientChip(String code, String label, String rule, long count, boolean hidden) {}

    public record BrandItem(String name, long count) {}

    public record Brands(List<BrandItem> top, long totalBrands) {}

    public record SellerCount(String code, String label, long count, boolean available) {}

    /** GET /rankings/filters — 필터 시트: 지금 조합의 제품 수, 영양성분 칩(숨김 여부), 많이 만드는 브랜드 8, 판매처. */
    public record FilterSheetResponse(long count, List<NutrientChip> nutrients, Brands brands, List<SellerCount> sellers) {}

    /** GET /rankings/brands — 브랜드 더 보기(「제조 ○○」 포함, 가나다순). */
    public record BrandListResponse(long total, List<BrandItem> items) {}

    public record AdminChip(String code, String name, String majorCode, boolean aggregate,
                            long total, long analyzed, boolean open, String reason) {}
}
