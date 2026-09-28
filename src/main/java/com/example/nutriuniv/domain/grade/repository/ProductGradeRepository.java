package com.example.nutriuniv.domain.grade.repository;

import com.example.nutriuniv.domain.goal.entity.GoalType;
import com.example.nutriuniv.domain.grade.entity.ProductGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductGradeRepository extends JpaRepository<ProductGrade, ProductGrade.Pk> {

    Optional<ProductGrade> findByProductIdAndGoalAndEerBand(Long productId, GoalType goal, int eerBand);

    List<ProductGrade> findByProductIdInAndGoalAndEerBand(Collection<Long> productIds, GoalType goal, int eerBand);

    /**
     * 조회 슬롯(일반=generalSlot, 그 외=band) 기준 목표별 등급 분포 — 재산출 전 분포(10.3).
     * 반환 행: [GoalType goal, Grade grade, Long count]
     */
    @Query("""
            SELECT g.goal, g.grade, COUNT(g.productId)
            FROM   ProductGrade g
            WHERE  (g.goal = :general AND g.eerBand = :generalSlot)
               OR  (g.goal <> :general AND g.eerBand = :band)
            GROUP BY g.goal, g.grade
            """)
    List<Object[]> countByGoalAndGradeAtSlot(@Param("general") GoalType general,
                                             @Param("generalSlot") int generalSlot,
                                             @Param("band") int band);
}
