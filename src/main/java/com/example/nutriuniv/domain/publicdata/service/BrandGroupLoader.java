package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.publicdata.compute.BrandGrouping;
import com.example.nutriuniv.domain.publicdata.dto.BrandGroupLoadResponse;
import com.example.nutriuniv.domain.publicdata.repository.BrandGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 묶음 구성표 올리기 — 통합 마스터의 묶음(food_cd → 묶음 키 · 대표 행)으로 brand_groups 를 통째로 바꾼다.
 * 쿠팡 보정 · 11-12 고침 · 쿠팡 상품명이 마스터 묶음 키에 걸려 있어, 이 표가 있어야 그 값들이 들어간다(없으면 모든 묶음이 새 키).
 * <pre>food_cd,group_key,is_representative   (is_representative: Y/N · true/false · 1/0)</pre>
 */
@Slf4j
@Service
public class BrandGroupLoader {

    static final List<String> HEADER = List.of("food_cd", "group_key", "is_representative");

    private final BrandGroupRepository repository;
    private final ProductRecomputeService recomputeService;
    private final TransactionTemplate txTemplate;

    public BrandGroupLoader(BrandGroupRepository repository, ProductRecomputeService recomputeService,
                            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.recomputeService = recomputeService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public BrandGroupLoadResponse load(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new CustomException(ErrorCode.BAD_REQUEST, "파일이 없습니다.");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CustomException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "csv 파일만 올릴 수 있습니다.");
        }
        if (recomputeService.isRunning()) throw new CustomException(ErrorCode.STATE_CONFLICT, "재계산이 도는 중입니다. 끝난 뒤 올려 주세요.");

        List<CsvReader.Record> records;
        try (Reader r = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            records = CsvReader.read(r);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일을 읽을 수 없습니다: " + e.getMessage());
        }
        Parsed p = parse(records);
        txTemplate.executeWithoutResult(s -> repository.replaceAll(p.rows(), LocalDateTime.now()));
        log.info("[BRAND] 묶음 구성표 올림 — file={} 행 {} · 묶음 {} · 건너뜀 {} · 중복 {} · 대표 없는 묶음 {}",
                name, p.rows().size(), p.groups(), p.skipped(), p.duplicates(), p.groupsWithoutRep());
        return new BrandGroupLoadResponse(p.rows().size(), p.groups(), p.skipped(), p.duplicates(), p.groupsWithoutRep());
    }

    record Parsed(List<BrandGrouping.Assigned> rows, int groups, int skipped, int duplicates, int groupsWithoutRep) {}

    /** 순수 함수(테스트용으로 열어 둠). */
    static Parsed parse(List<CsvReader.Record> records) {
        if (records.isEmpty()) throw new CustomException(ErrorCode.BAD_REQUEST, "빈 파일입니다.");
        Map<String, Integer> col = new HashMap<>();
        List<String> header = records.get(0).fields();
        for (int i = 0; i < header.size(); i++) col.put(header.get(i).strip().toLowerCase(Locale.ROOT), i);
        for (String h : HEADER) {
            if (!col.containsKey(h)) throw new CustomException(ErrorCode.BAD_REQUEST, "헤더에 " + h + " 가 없습니다. 필요한 헤더: " + HEADER);
        }
        Map<String, BrandGrouping.Assigned> rows = new LinkedHashMap<>();
        int skipped = 0, duplicates = 0;
        for (CsvReader.Record rec : records.subList(1, records.size())) {
            String foodCd = rec.get(col.get("food_cd")).strip();
            String key = rec.get(col.get("group_key")).strip();
            Boolean rep = flag(rec.get(col.get("is_representative")));
            if (foodCd.isEmpty() || key.isEmpty() || key.length() > 32 || foodCd.length() > 40 || rep == null) {
                skipped++;
                continue;
            }
            if (rows.containsKey(foodCd)) {
                duplicates++;
                continue;
            }
            rows.put(foodCd, new BrandGrouping.Assigned(foodCd, key, rep));
        }
        Set<String> groups = new HashSet<>();
        Set<String> withRep = new HashSet<>();
        for (BrandGrouping.Assigned a : rows.values()) {
            groups.add(a.groupKey());
            if (a.representative()) withRep.add(a.groupKey());
        }
        return new Parsed(List.copyOf(rows.values()), groups.size(), skipped, duplicates, groups.size() - withRep.size());
    }

    static Boolean flag(String raw) {
        String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "y", "yes", "true", "1", "t" -> true;
            case "n", "no", "false", "0", "f", "" -> false;
            default -> null;
        };
    }
}