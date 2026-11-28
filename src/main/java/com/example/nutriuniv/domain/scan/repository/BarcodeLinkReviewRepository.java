package com.example.nutriuniv.domain.scan.repository;

import com.example.nutriuniv.domain.scan.entity.BarcodeLinkReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BarcodeLinkReviewRepository extends JpaRepository<BarcodeLinkReview, Long> {

    Optional<BarcodeLinkReview> findByBarcodeAndProductId(String barcode, Long productId);

    Page<BarcodeLinkReview> findByStatusOrderByCreatedAtDesc(BarcodeLinkReview.Status status, Pageable pageable);
}
