package com.example.nutriuniv.domain.ranking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스용 랭킹 분류 (기능명세서 6.3). 공공 데이터 분류(categories)를 그대로 쓰지 않는다 — 가장 큰 분류가
 * 「해당없음(과자류, 빵류 또는 떡류)」 7.4만 건이라 순위가 의미를 잃는다. 분류 체계는 팀 결정 대기(「아직 정하지 않은 것」).
 * <p>이 표가 비어 있으면 랭킹은 「아직 열린 분류 없음」으로 동작한다(빈 목록). 채우는 방법은 db/manual/08_ranking_commerce.sql.
 * 어느 categories 행이 이 분류에 속하는지는 {@link RankingCategoryMapping}.
 */
@Entity
@Table(name = "ranking_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_ranking_categories_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** 랭킹 화면에서 처음 선택되는 탭. 여러 개면 display_order 가 가장 작은 것. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static RankingCategory create(String name, int displayOrder, boolean isDefault) {
        RankingCategory c = new RankingCategory();
        c.name         = name;
        c.displayOrder = displayOrder;
        c.isDefault    = isDefault;
        c.isActive     = true;
        c.createdAt    = LocalDateTime.now();
        return c;
    }
}
