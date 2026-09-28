package com.example.nutriuniv.domain.goal.repository;

import com.example.nutriuniv.domain.goal.entity.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserGoalRepository extends JpaRepository<UserGoal, Long> {

    Optional<UserGoal> findByUserId(Long userId);

    Optional<UserGoal> findByAnonymousId(String anonymousId);
}
