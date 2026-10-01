package com.example.nutriuniv.domain.ranking.repository;

import com.example.nutriuniv.domain.ranking.entity.RankingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RankingCategoryRepository extends JpaRepository<RankingCategory, Long> {

    List<RankingCategory> findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<RankingCategory> findByIdAndIsActiveTrue(Long id);

    long countByIsActiveTrue();
}
