package com.example.nutriuniv.domain.history.repository;

import com.example.nutriuniv.domain.history.entity.ViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {

    /** 로그인 병합 — 기기 기록을 계정으로. 옮긴 행 수. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ViewHistory h SET h.userId = :userId, h.anonymousId = null WHERE h.anonymousId = :anonymousId")
    int transferToUser(@Param("anonymousId") String anonymousId, @Param("userId") Long userId);
}
