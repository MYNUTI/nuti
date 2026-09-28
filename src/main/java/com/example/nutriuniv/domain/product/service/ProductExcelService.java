package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.product.dto.ProductUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductExcelService {

    private final ProductExcelRowService rowService;

    // 헤더명 -> 필드 키 매핑 테이블
    private static final Map<String, String> HEADER_MAP = Map.ofEntries(
            Map.entry("제품명",           "name"),
            Map.entry("브랜드",           "brand"),
            Map.entry("대분류",           "categoryDepth1"),
            Map.entry("중분류",           "categoryDepth2"),
            Map.entry("바코드",           "barcode"),
            Map.entry("서빙사이즈",        "servingSize"),
            Map.entry("열량_kcal",        "calories"),
            Map.entry("열량kcal",         "calories"),
            Map.entry("탄수화물",         "carbohydrate"),
            Map.entry("당",              "sugar"),
            Map.entry("설탕",            "sugar"),
            Map.entry("단백질",           "protein"),
            Map.entry("지방",            "fat"),
            Map.entry("포화지방",         "saturatedFat"),
            Map.entry("트랜스지방",        "transFat"),
            Map.entry("콜레스테롤",        "cholesterol"),
            Map.entry("나트륨",           "sodium"),
            Map.entry("식이섬유",         "fiber"),
            Map.entry("100g_열량_kcal",   "caloriesPer100g"),
            Map.entry("100g_탄수화물",    "carbohydratePer100g"),
            Map.entry("100g_당",         "sugarPer100g"),
            Map.entry("100g_단백질",      "proteinPer100g"),
            Map.entry("100g_지방",       "fatPer100g"),
            Map.entry("100g_포화지방",    "saturatedFatPer100g"),
            Map.entry("100g_트랜스지방",  "transFatPer100g"),
            Map.entry("100g_콜레스테롤",  "cholesterolPer100g"),
            Map.entry("100g_나트륨",     "sodiumPer100g"),
            Map.entry("100g_식이섬유",    "fiberPer100g")
    );

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "name", "brand", "categoryDepth1", "categoryDepth2"
    );

    // ── 엑셀 업로드 메인 ──────────────────────────────────────────────────────────
    // @Transactional 제거 — 각 행이 ProductExcelRowService에서 독립 트랜잭션으로 처리됨

    public ProductUploadResponse upload(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일이 없습니다.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new CustomException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "xlsx 파일만 업로드 가능합니다.");
        }

        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일을 읽을 수 없습니다.");
        }

        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "헤더 행이 없습니다.");
        }

        Map<Integer, String> colIndexToKey = parseHeader(headerRow);
        validateRequiredColumns(colIndexToKey);

        int totalCount = 0;
        int successCount = 0;
        List<ProductUploadResponse.FailItem> failItems = new ArrayList<>();

        // 브랜드/카테고리 캐시 (행마다 DB 조회 방지)
        Map<String, Brand> brandCache  = new HashMap<>();
        Map<String, Category> catCache = new HashMap<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowBlank(row)) continue;

            totalCount++;
            int rowNumber = i + 1;

            try {
                Map<String, String> values = extractValues(row, colIndexToKey);
                rowService.processRow(values, brandCache, catCache);
                successCount++;
            } catch (Exception e) {
                String productName = getCellString(row, getColIndex(colIndexToKey, "name"));
                log.warn("[ExcelUpload] row={} name={} error={}", rowNumber, productName, e.getMessage());
                failItems.add(ProductUploadResponse.FailItem.builder()
                        .rowNumber(rowNumber)
                        .productName(productName)
                        .reason(e.getMessage())
                        .build());
            }
        }

        return ProductUploadResponse.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failItems.size())
                .failItems(failItems)
                .build();
    }

    // ── 헤더 파싱 ─────────────────────────────────────────────────────────────────

    private Map<Integer, String> parseHeader(Row headerRow) {
        Map<Integer, String> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String normalized = normalize(getCellString(headerRow, cell.getColumnIndex()));
            for (Map.Entry<String, String> entry : HEADER_MAP.entrySet()) {
                if (normalize(entry.getKey()).equals(normalized)) {
                    map.put(cell.getColumnIndex(), entry.getValue());
                    break;
                }
            }
        }
        return map;
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\(.*?\\)", "")
                .replaceAll("\\s+", "")
                .toLowerCase();
    }

    private void validateRequiredColumns(Map<Integer, String> colIndexToKey) {
        Set<String> found = new HashSet<>(colIndexToKey.values());
        for (String required : REQUIRED_KEYS) {
            if (!found.contains(required)) {
                throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY,
                        "필수 컬럼이 누락되었습니다: " + required);
            }
        }
    }

    // ── 셀 값 추출 ────────────────────────────────────────────────────────────────

    private Map<String, String> extractValues(Row row, Map<Integer, String> colIndexToKey) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<Integer, String> entry : colIndexToKey.entrySet()) {
            values.put(entry.getValue(), getCellString(row, entry.getKey()));
        }
        return values;
    }

    private String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? String.valueOf(cell.getNumericCellValue())
                    : cell.getStringCellValue().trim();
            default -> "";
        };
    }

    private Integer getColIndex(Map<Integer, String> colIndexToKey, String key) {
        return colIndexToKey.entrySet().stream()
                .filter(e -> key.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(-1);
    }

    private boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }
}
