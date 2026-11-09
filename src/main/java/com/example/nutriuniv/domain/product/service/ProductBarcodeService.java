package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductBarcode;
import com.example.nutriuniv.domain.product.repository.ProductBarcodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 제품–바코드 연결 (v3 DATA_000, N:M) — 적재와 관리자 수정이 같은 방식으로 맞춘다. */
@Service
@RequiredArgsConstructor
public class ProductBarcodeService {

    private final ProductBarcodeRepository barcodeRepository;

    /** 제품의 바코드를 정확히 이 목록(13자리 정규화 값)으로 맞춘다. 바뀌었으면 true. */
    @Transactional
    public boolean replace(Product product, Collection<String> normalized13) {
        Set<String> wanted = new LinkedHashSet<>(normalized13);
        boolean changed = false;
        for (ProductBarcode b : barcodeRepository.findByProductId(product.getId())) {
            if (!wanted.remove(b.getBarcode())) {
                barcodeRepository.delete(b);
                changed = true;
            }
        }
        for (String code : wanted) {
            barcodeRepository.save(ProductBarcode.of(product, code));
            changed = true;
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public List<String> barcodesOf(Long productId) {
        return barcodeRepository.findByProductId(productId).stream().map(ProductBarcode::getBarcode).sorted().toList();
    }
}
