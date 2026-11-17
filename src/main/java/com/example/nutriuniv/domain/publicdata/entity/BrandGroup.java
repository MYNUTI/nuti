package com.example.nutriuniv.domain.publicdata.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 묶음 구성표 (brand.md 조회표 3) — 영양성분DB food_cd → 묶음 키(16자리) · 대표 행 여부.
 * <ul>
 *   <li>한 번 정한 food_cd 의 묶음은 바꾸지 않는다(쿠팡 보정 · 11-12 고침이 묶음 키에 걸려 있다)</li>
 *   <li>재계산이 새 food_cd 의 묶음을 정해 더한다. 통합 마스터의 묶음은 관리자 API 로 한 번에 올린다(POST /admin/product-sync/brand-groups)</li>
 * </ul>
 * 읽기 · 쓰기는 BrandGroupRepository(JDBC). 이 엔티티는 표를 만들기(ddl-auto) 위한 정의다.
 */
@Entity
@Table(name = "brand_groups", indexes = @Index(name = "idx_brand_groups_group_key", columnList = "group_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandGroup {

    @Id
    @Column(name = "food_cd", length = 40)
    private String foodCd;

    @Column(name = "group_key", nullable = false, length = 32)
    private String groupKey;

    @Column(name = "is_representative", nullable = false)
    private boolean representative;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}