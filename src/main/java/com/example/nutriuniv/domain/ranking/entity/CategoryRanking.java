package com.example.nutriuniv.domain.ranking.entity;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.Grade;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 분류×목표별 순위 한 행 (기능명세서 6.3 「매일 새벽 배치로 만들고 조회는 읽기만 한다」).
 * 쓰기는 RankingBatchService 가 JDBC 로 분류 단위 DELETE + INSERT 한다. 이 엔티티는 DDL·조회용.
 * 순위와 등급을 함께 저장한다 — 「1위인데 D등급」을 그대로 보여줘야 한다.
 */
@Entity
@Table(name = "category_rankings",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_rankings_slot", columnNames = {"ranking_category_id", "goal", "rank_no"}),
        indexes = @Index(name = "idx_category_rankings_product", columnList = "product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ranking_category_id", nullable = false)
    private Long rankingCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalType goal;

    @Column(name = "rank_no", nullable = false)
    private int rank;                       // 컬럼명은 rank_no — rank 는 SQL 창 함수 이름이라 피한다

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Grade grade;

    @Column(nullable = false, precision = 6, scale = 1)
    private BigDecimal score;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
