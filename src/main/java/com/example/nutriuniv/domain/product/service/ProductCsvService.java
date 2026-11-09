package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.category.repository.CategoryRepository;
import com.example.nutriuniv.domain.product.dto.ProductUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 제품 적재 (v3 DATA_000) — 데이터 팀 최종 테이블 CSV(UTF-8)를 받아 master_id 기준으로 갱신한다. 이름으로 합치지 않는다.
 * <ul>
 *   <li>같은 master_id 행이 여러 개면 한 제품으로 묶고 바코드를 모은다</li>
 *   <li>content_hash 가 이전과 같으면 건너뛴다 · 운영자가 고친 제품은 건너뛴다</li>
 *   <li>바코드는 13자리로 맞춰 제품–바코드 표에 넣는다(N:M) · category 는 중분류 코드(비면 분류 제외)</li>
 *   <li>제품마다 독립 트랜잭션 — 실패한 제품은 줄 번호와 이유를 돌려주고, 이전 값이 남는다</li>
 *   <li>적재 중에는 다른 적재·등급 기준 재산출을 막는다(ProductLoadService)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCsvService {

    static final String COL_MASTER_ID    = "master_id";
    static final String COL_BARCODE      = "barcode";
    static final String COL_REPORT_NO    = "report_no";
    static final String COL_SOURCE       = "source";
    static final String COL_NAME         = "product_name";
    static final String COL_BRAND        = "brand";
    static final String COL_CATEGORY     = "category";
    static final String COL_MAKER        = "maker";
    static final String COL_INGREDIENTS  = "ingredients";
    static final String COL_SERVING_SIZE = "serving_size";
    static final String COL_DISCONTINUED = "is_discontinued";
    static final String COL_HASH         = "content_hash";
    // 같이 보이는 칩(최대 2개) — 데이터 팀이 열을 추가하기 전까지의 가정: 열 이름 extra_categories, 중분류 코드를 | 로 구분. 열이 없으면 칩은 그대로 둔다
    static final String COL_EXTRA_CHIPS  = "extra_categories";

    static final List<String> NUTRIENT_COLUMNS = List.of(
            "calories_kcal", "sodium_mg", "carbohydrate_g", "sugar_g", "fat_g",
            "trans_fat_g", "saturated_fat_g", "cholesterol_mg", "dietary_fiber_g", "protein_g");

    static final List<String> REQUIRED_COLUMNS;
    static {
        List<String> cols = new ArrayList<>(List.of(COL_MASTER_ID, COL_BARCODE, COL_REPORT_NO, COL_NAME, COL_BRAND,
                COL_CATEGORY, COL_DISCONTINUED, COL_HASH));
        cols.addAll(NUTRIENT_COLUMNS);
        NUTRIENT_COLUMNS.forEach(c -> cols.add(c + "_per_100g"));
        REQUIRED_COLUMNS = List.copyOf(cols);
    }

    private final ProductCsvRowService rowService;
    private final ProductLoadService loadService;
    private final CategoryRepository categoryRepository;

    public ProductUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일이 없습니다.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CustomException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "csv 파일만 올릴 수 있습니다.");
        }

        List<CsvReader.Record> records = read(file);
        if (records.isEmpty()) throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "헤더 행이 없습니다.");
        Map<String, Integer> header = header(records.get(0));
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "필수 열이 없습니다: " + String.join(", ", missing));
        }
        boolean withChips = header.containsKey(COL_EXTRA_CHIPS);

        List<ProductUploadResponse.FailItem> fails = new ArrayList<>();
        List<ProductCsvRowService.LoadItem> items = group(records, header, fails);

        Long loadId = loadService.start(filename);
        int created = 0, updated = 0, unchanged = 0, protectedRows = 0;
        try {
            Map<String, Category> categoriesByCode = new HashMap<>();
            for (Category c : categoryRepository.findAllByCodeIsNotNull()) categoriesByCode.put(c.getCode(), c);
            Map<String, Brand> brandCache = new HashMap<>();

            for (ProductCsvRowService.LoadItem item : items) {
                try {
                    switch (rowService.apply(item, categoriesByCode, brandCache, withChips)) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case UNCHANGED -> unchanged++;
                        case PROTECTED -> protectedRows++;
                    }
                } catch (Exception e) {
                    brandCache.clear();   // 실패한 트랜잭션에서 만든 브랜드가 캐시에 남지 않게
                    log.warn("[ProductLoad] line={} master_id={} error={}", item.line(), item.masterId(), e.getMessage());
                    fails.add(fail(item.line(), item.masterId(), item.get(COL_NAME), e.getMessage()));
                }
            }
        } catch (RuntimeException e) {
            loadService.fail(loadId);
            throw e;
        }
        int total = items.size() + (int) fails.stream().filter(f -> f.getMasterId() == null).count();
        loadService.finish(loadId, total, created, updated, unchanged, protectedRows, fails.size());
        log.info("[ProductLoad] id={} file={} 제품 {} — 새로 {} · 수정 {} · 그대로 {} · 보호 {} · 실패 {}",
                loadId, filename, total, created, updated, unchanged, protectedRows, fails.size());

        fails.sort(Comparator.comparingInt(ProductUploadResponse.FailItem::getRowNumber));
        return ProductUploadResponse.builder()
                .loadId(loadId)
                .totalCount(total)
                .successCount(created + updated + unchanged + protectedRows)
                .createdCount(created)
                .updatedCount(updated)
                .unchangedCount(unchanged)
                .protectedCount(protectedRows)
                .failCount(fails.size())
                .failItems(fails)
                .build();
    }

    /** UTF-8 로만 읽는다 — 엑셀 기본 저장(CP949)이면 한글이 깨진 채 들어가므로 400 으로 돌려보낸다. */
    private static List<CsvReader.Record> read(MultipartFile file) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader r = new InputStreamReader(file.getInputStream(), decoder)) {
            return CsvReader.read(r);
        } catch (CharacterCodingException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "UTF-8 로 저장한 CSV 만 받습니다.");
        } catch (IOException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일을 읽을 수 없습니다.");
        }
    }

    static Map<String, Integer> header(CsvReader.Record headerRow) {
        Map<String, Integer> map = new HashMap<>();
        List<String> fields = headerRow.fields();
        for (int i = 0; i < fields.size(); i++) {
            map.putIfAbsent(fields.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return map;
    }

    /** master_id 별로 묶는다(파일 순서 유지). master_id 가 빈 행은 실패로 넣는다. */
    static List<ProductCsvRowService.LoadItem> group(List<CsvReader.Record> records, Map<String, Integer> header,
                                                     List<ProductUploadResponse.FailItem> fails) {
        int idCol = header.get(COL_MASTER_ID);
        int barcodeCol = header.get(COL_BARCODE);
        int nameCol = header.get(COL_NAME);
        Map<String, ProductCsvRowService.LoadItem> byId = new LinkedHashMap<>();
        for (CsvReader.Record rec : records.subList(1, records.size())) {
            String masterId = rec.get(idCol).trim();
            if (masterId.isEmpty()) {
                fails.add(fail(rec.line(), null, rec.get(nameCol).trim(), "master_id 가 비어 있습니다."));
                continue;
            }
            ProductCsvRowService.LoadItem item = byId.get(masterId);
            if (item == null) {
                Map<String, String> values = new HashMap<>();
                header.forEach((col, idx) -> values.put(col, rec.get(idx)));
                item = new ProductCsvRowService.LoadItem(rec.line(), masterId, values, new ArrayList<>());
                byId.put(masterId, item);
            }
            item.rawBarcodes().add(rec.get(barcodeCol));
        }
        return new ArrayList<>(byId.values());
    }

    private static ProductUploadResponse.FailItem fail(int line, String masterId, String name, String reason) {
        return ProductUploadResponse.FailItem.builder()
                .rowNumber(line).masterId(masterId).productName(name).reason(reason).build();
    }
}
