package com.example.nutriuniv.domain.scan.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import com.example.nutriuniv.domain.product.util.BarcodeNormalizer;
import com.example.nutriuniv.domain.scan.dto.BarcodeReviewDtos;
import com.example.nutriuniv.domain.scan.entity.BarcodeLinkReview;
import com.example.nutriuniv.domain.scan.entity.BarcodeName;
import com.example.nutriuniv.domain.scan.repository.BarcodeLinkReviewRepository;
import com.example.nutriuniv.domain.scan.repository.BarcodeNameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 운영 「확인 대기」 목록 · 이름을 아는 바코드 CSV 적재 (SCAN_008 · SCAN_009 · SCAN_010). */
@Service
@RequiredArgsConstructor
public class BarcodeAdminService {

    static final String COL_BARCODE = "barcode";
    static final String COL_NAME = "product_name";
    static final String COL_COMPANY = "company";

    private final BarcodeLinkReviewRepository reviewRepository;
    private final BarcodeNameRepository nameRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public BarcodeReviewDtos.Page reviews(String statusParam, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "page는 0 이상, size는 1~100이어야 합니다.");
        BarcodeLinkReview.Status status;
        try {
            status = statusParam == null || statusParam.isBlank()
                    ? BarcodeLinkReview.Status.PENDING : BarcodeLinkReview.Status.valueOf(statusParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM, "status 는 PENDING · CONFIRMED · REJECTED 중 하나여야 합니다.");
        }
        Page<BarcodeLinkReview> p = reviewRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
        Map<Long, String> names = productRepository.findAllById(p.getContent().stream().map(BarcodeLinkReview::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        List<BarcodeReviewDtos.Item> items = p.getContent().stream()
                .map(r -> BarcodeReviewDtos.Item.of(r, names.get(r.getProductId()))).toList();
        return new BarcodeReviewDtos.Page(items, p.getTotalElements(), page, size);
    }

    public BarcodeReviewDtos.Item item(BarcodeLinkReview r) {
        return BarcodeReviewDtos.Item.of(r, productRepository.findById(r.getProductId()).map(Product::getName).orElse(null));
    }

    /** CSV(UTF-8) 열: barcode, product_name, company(선택). 같은 바코드는 고친다. 바코드 형식이 틀리면 그 줄만 건너뛴다. */
    @Transactional
    public BarcodeReviewDtos.NameUploadResponse uploadNames(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new CustomException(ErrorCode.BAD_REQUEST, "파일이 없습니다.");
        List<CsvReader.Record> records;
        try (Reader in = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            records = CsvReader.read(in);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일을 읽지 못했습니다.");
        }
        if (records.isEmpty()) throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "헤더 행이 없습니다.");
        Map<String, Integer> header = new HashMap<>();
        List<String> cols = records.get(0).fields();
        for (int i = 0; i < cols.size(); i++) header.put(cols.get(i).replace("﻿", "").trim().toLowerCase(Locale.ROOT), i);
        if (!header.containsKey(COL_BARCODE) || !header.containsKey(COL_NAME)) {
            throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "필수 열이 없습니다: barcode, product_name");
        }
        Function<CsvReader.Record, String> company = header.containsKey(COL_COMPANY)
                ? r -> blankToNull(r.get(header.get(COL_COMPANY))) : r -> null;

        int created = 0, updated = 0;
        List<String> failed = new ArrayList<>();
        for (CsvReader.Record r : records.subList(1, records.size())) {
            String name = blankToNull(r.get(header.get(COL_NAME)));
            String barcode;
            try {
                barcode = BarcodeNormalizer.normalize(r.get(header.get(COL_BARCODE)));
            } catch (CustomException e) {
                failed.add(r.line() + "행: 바코드 형식");
                continue;
            }
            if (name == null) {
                failed.add(r.line() + "행: 제품명 없음");
                continue;
            }
            String n = cut(name, 200), c = cut(company.apply(r), 100);
            var found = nameRepository.findByBarcode(barcode);
            if (found.isPresent()) {
                found.get().update(n, c);
                updated++;
            } else {
                nameRepository.save(BarcodeName.of(barcode, n, c));
                created++;
            }
        }
        return new BarcodeReviewDtos.NameUploadResponse(records.size() - 1, created, updated, failed);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
