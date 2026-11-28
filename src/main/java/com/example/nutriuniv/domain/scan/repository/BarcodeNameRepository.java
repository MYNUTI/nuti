package com.example.nutriuniv.domain.scan.repository;

import com.example.nutriuniv.domain.scan.entity.BarcodeName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BarcodeNameRepository extends JpaRepository<BarcodeName, Long> {

    Optional<BarcodeName> findByBarcode(String barcode);
}
