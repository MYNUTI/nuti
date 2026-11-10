package com.example.nutriuniv.domain.body.repository;

import com.example.nutriuniv.domain.body.entity.BodyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BodyProfileRepository extends JpaRepository<BodyProfile, Long> {

    Optional<BodyProfile> findByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BodyProfile b WHERE b.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
