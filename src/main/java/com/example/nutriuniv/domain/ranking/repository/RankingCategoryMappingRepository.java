package com.example.nutriuniv.domain.ranking.repository;

import com.example.nutriuniv.domain.ranking.entity.RankingCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingCategoryMappingRepository extends JpaRepository<RankingCategoryMapping, Long> {

    long countByRankingCategoryId(Long rankingCategoryId);
}
