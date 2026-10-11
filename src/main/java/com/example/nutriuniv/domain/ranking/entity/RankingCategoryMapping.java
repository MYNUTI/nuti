package com.example.nutriuniv.domain.ranking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스 분류 ↔ 공공 분류(categories) 매핑. 한 categories 행은 한 서비스 분류에만 속한다(category_id UNIQUE).
 * <p>매핑은 <b>하위 분류를 포함</b>한다 — 대분류를 매핑하면 그 아래 중·소분류 제품이 전부 들어간다.
 * 상위와 하위가 서로 다른 서비스 분류에 매핑돼 있으면 <b>더 가까운(하위) 매핑</b>이 이긴다 (RankingBatchRepository 의 재귀 CTE).
 * 이 규칙 덕에 「과자류 전체는 A, 그중 초콜릿만 B」 같은 지정이 행 두 개로 끝난다.
 */
@Entity
@Table(name = "ranking_category_mappings",
        uniqueConstraints = @UniqueConstraint(name = "uk_ranking_category_mappings_category", columnNames = "category_id"),
        indexes = @Index(name = "idx_ranking_category_mappings_ranking", columnList = "ranking_category_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingCategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ranking_category_id", nullable = false)
    private Long rankingCategoryId;

    /** categories.id — 이 분류와 그 하위 전체. */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static RankingCategoryMapping create(Long rankingCategoryId, Long categoryId) {
        RankingCategoryMapping m = new RankingCategoryMapping();
        m.rankingCategoryId = rankingCategoryId;
        m.categoryId        = categoryId;
        m.createdAt         = LocalDateTime.now();
        return m;
    }
}
