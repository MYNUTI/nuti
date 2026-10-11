package com.example.nutriuniv.domain.ranking.repository;

import com.example.nutriuniv.domain.ranking.entity.RankingCategoryStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RankingCategoryStatRepository extends JpaRepository<RankingCategoryStat, Long> {

    List<RankingCategoryStat> findByRankingCategoryIdIn(Collection<Long> rankingCategoryIds);
}
