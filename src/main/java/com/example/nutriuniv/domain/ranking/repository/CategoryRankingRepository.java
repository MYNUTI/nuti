package com.example.nutriuniv.domain.ranking.repository;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.ranking.entity.CategoryRanking;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRankingRepository extends JpaRepository<CategoryRanking, Long> {

    List<CategoryRanking> findByRankingCategoryIdAndGoalOrderByRankAsc(Long rankingCategoryId, GoalType goal, Pageable pageable);

    long countByRankingCategoryIdAndGoal(Long rankingCategoryId, GoalType goal);
}
