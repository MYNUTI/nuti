package com.example.nutriuniv.domain.product.repository;

import com.example.nutriuniv.domain.product.entity.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, Long> {

    List<ProductBarcode> findByProductId(Long productId);
}
