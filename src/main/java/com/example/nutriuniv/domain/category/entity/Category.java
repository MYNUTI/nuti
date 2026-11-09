package com.example.nutriuniv.domain.category.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 분류 목록 (v3 DATA_005) — 대분류 15개(depth 1, 코드 2자리) · 중분류 90개(depth 2, 코드 4자리) + 합산 칩.
 * 코드가 있는 행은 CategorySeeder 가 db/seed/categories.csv 로 맞춘다. 제품은 코드로 소속 중분류를 찾는다.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // 데이터 팀 분류 코드 — 대분류 2자리(01), 중분류 4자리(0101). 예전 엑셀 적재·관리자 등록 행은 비어 있다
    @Column(length = 4, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    // 합산 칩 — 다른 중분류 제품을 모아 보여 주는 칩(예: 다이어트·단백질 › 단백질 음료). 목록은 데이터 팀에서 받는다
    @Column(name = "is_aggregate", nullable = false, columnDefinition = "boolean default false")
    private boolean aggregate = false;

    @Column(nullable = false, columnDefinition = "int default 1")
    private int depth = 1;

    @Column(name = "display_order", nullable = false, columnDefinition = "int default 0")
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    private boolean isActive = true;

    public static Category createDepth1(String name) {
        Category c = new Category();
        c.name = name;
        c.depth = 1;
        return c;
    }

    public static Category createDepth2(String name, Category parent) {
        Category c = new Category();
        c.name = name;
        c.depth = 2;
        c.parent = parent;
        return c;
    }

    // 관리자 등록용 (depth 1~3 공통)
    public static Category create(String name, int depth, Category parent, int displayOrder) {
        Category c = new Category();
        c.name = name;
        c.depth = depth;
        c.parent = parent;
        c.displayOrder = displayOrder;
        return c;
    }

    /** 시드 파일의 한 줄로 새 분류를 만든다. */
    public static Category seeded(String code, String name, Category parent, int displayOrder) {
        Category c = new Category();
        c.code = code;
        c.name = name;
        c.depth = parent == null ? 1 : 2;
        c.parent = parent;
        c.displayOrder = displayOrder;
        return c;
    }

    /** 시드 파일과 다르면 맞춘다. 바뀌었으면 true. */
    public boolean applySeed(String name, Category parent, int displayOrder) {
        boolean changed = !name.equals(this.name) || !java.util.Objects.equals(idOf(this.parent), idOf(parent))
                || this.displayOrder != displayOrder || !this.isActive;
        this.name = name;
        this.parent = parent;
        this.depth = parent == null ? 1 : 2;
        this.displayOrder = displayOrder;
        this.isActive = true;
        return changed;
    }

    private static Long idOf(Category c) {
        return c == null ? null : c.getId();
    }

    // 관리자 수정용
    public void update(String name, Integer displayOrder) {
        if (name != null)         this.name         = name;
        if (displayOrder != null) this.displayOrder = displayOrder;
    }

    public void deactivate() {
        this.isActive = false;
    }
}