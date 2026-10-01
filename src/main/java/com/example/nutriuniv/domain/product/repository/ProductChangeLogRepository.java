package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.product.entity.ProductChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductChangeLogRepository extends JpaRepository<ProductChangeLog, Long> {

    List<ProductChangeLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
