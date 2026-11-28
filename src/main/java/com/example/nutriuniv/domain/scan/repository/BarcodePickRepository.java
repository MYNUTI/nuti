package com.example.nutriuniv.domain.scan.repository;

import com.example.nutriuniv.domain.scan.entity.BarcodePick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BarcodePickRepository extends JpaRepository<BarcodePick, Long> {

    Optional<BarcodePick> findByBarcodeAndOwnerKey(String barcode, String ownerKey);

    long countByBarcodeAndProductId(String barcode, Long productId);

    /** 바코드의 제품별 고른 사람 수 — [productId, count]. */
    @Query("SELECT p.productId, COUNT(p) FROM BarcodePick p WHERE p.barcode = :barcode GROUP BY p.productId")
    List<Object[]> countByProduct(@Param("barcode") String barcode);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BarcodePick p WHERE p.barcode = :barcode AND p.productId = :productId")
    int deleteByBarcodeAndProductId(@Param("barcode") String barcode, @Param("productId") Long productId);
}
