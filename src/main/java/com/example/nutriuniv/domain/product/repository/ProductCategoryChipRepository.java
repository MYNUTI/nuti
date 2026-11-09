package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.product.entity.ProductCategoryChip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryChipRepository extends JpaRepository<ProductCategoryChip, Long> {

    List<ProductCategoryChip> findByProductId(Long productId);
}
