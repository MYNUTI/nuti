package com.example.nutriuniv.domain.publicdata.dto;

/** 묶음 구성표 올리기 결과 — POST /admin/product-sync/brand-groups. */
public record BrandGroupLoadResponse(
        int rows,              // 넣은 food_cd
        int groups,            // 묶음 수
        int skipped,           // 비었거나 형식이 틀려 건너뛴 줄
        int duplicates,        // 같은 food_cd 가 다시 나온 줄 (첫 줄을 씀)
        int groupsWithoutRep   // 대표 행이 없는 묶음 (재계산 때 가장 작은 food_cd 를 대표로 씀)
) {}