package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.product.entity.ProductLoad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductLoadRepository extends JpaRepository<ProductLoad, Long> {

    boolean existsByStatusAndStartedAtAfter(ProductLoad.Status status, LocalDateTime startedAfter);

    Optional<ProductLoad> findTopByStatusOrderByFinishedAtDesc(ProductLoad.Status status);
}
