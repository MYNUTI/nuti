package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.product.entity.ProductLoad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductLoadRepository extends JpaRepository<ProductLoad, Long> {

    boolean existsByStatusAndStartedAtAfter(ProductLoad.Status status, LocalDateTime startedAfter);

    Optional<ProductLoad> findTopByStatusOrderByFinishedAtDesc(ProductLoad.Status status);

    /** 서버가 적재 도중 꺼져 RUNNING 으로 남은 기록을 실패로 돌린다(부팅 때). */
    @Modifying
    @Query("UPDATE ProductLoad l SET l.status = com.example.nutriuniv.domain.product.entity.ProductLoad.Status.FAILED, " +
            "l.finishedAt = :now " +
            "WHERE l.status = com.example.nutriuniv.domain.product.entity.ProductLoad.Status.RUNNING")
    int failInterrupted(@Param("now") LocalDateTime now);
}
