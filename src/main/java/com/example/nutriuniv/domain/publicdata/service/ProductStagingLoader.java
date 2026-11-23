package com.example.nutriuniv.domain.publicdata.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.util.CsvReader;
import com.example.nutriuniv.domain.product.service.ProductLoadService;
import com.example.nutriuniv.domain.product.util.ContentHashCalculator;
import com.example.nutriuniv.domain.publicdata.dto.StagingLoadResponse;
import com.example.nutriuniv.domain.publicdata.repository.ProductStagingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 34컬럼 CSV(파이프라인 product_master.csv 형식)로 복제 테이블을 통째로 채운다.
 * 입력은 두 가지 — 관리자가 올린 파일(POST /admin/product-sync/staging), 파이프라인이 /data/csv 에 둔 파일(CSV 수신함).
 * <ul>
 *   <li>복제 테이블은 「지금 원천 전체」를 뜻한다 — 비우고 채우기를 한 트랜잭션으로(중간에 실패하면 이전 내용 그대로)</li>
 *   <li>content_hash 는 파일 값이 아니라 ContentHashCalculator 로 다시 계산해 넣는다. 파일 값과 다르면 건수와 예시를 돌려준다(계산기 대조)</li>
 *   <li>같은 master_id 가 두 번 나오면 첫 행만 넣고 나머지는 건너뛴 건수로 센다(34컬럼은 제품당 한 행)</li>
 *   <li>extra_categories(같이 보이는 칩) 열은 있으면 받는다(없어도 됨). hash 에는 넣지 않고 정리한 값만 따로 둔다</li>
 *   <li>실제 product 는 건드리지 않는다 — 반영은 ProductSyncApplyService</li>
 * </ul>
 */
@Slf4j
@Service
public class ProductStagingLoader {

    static final List<String> REQUIRED_COLUMNS;
    static {
        List<String> cols = new ArrayList<>();
        cols.add("master_id");
        cols.addAll(ContentHashCalculator.HASH_COLUMNS);
        REQUIRED_COLUMNS = List.copyOf(cols);
    }
    private static final int SAMPLE_LIMIT = 20;
    private static final String SPLIT = "[|,;\\s]+";

    private final ProductStagingRepository stagingRepository;
    private final ProductLoadService loadService;
    private final TransactionTemplate txTemplate;

    public ProductStagingLoader(ProductStagingRepository stagingRepository, ProductLoadService loadService,
                                PlatformTransactionManager transactionManager) {
        this.stagingRepository = stagingRepository;
        this.loadService = loadService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 파싱 결과 — 넣을 행 · 건너뛴 행 · 파일 hash 와 다른 행. */
    record Parsed(List<Map<String, String>> rows, int duplicateCount, int missingIdCount,
                  int hashMismatchCount, List<String> hashMismatchSamples) {}

    public StagingLoadResponse load(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일이 없습니다.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CustomException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "csv 파일만 올릴 수 있습니다.");
        }
        return load(filename, file::getInputStream);
    }

    /** 서버 디스크의 34컬럼 CSV(파이프라인이 /data/csv 에 둔 파일)로 복제 테이블을 채운다 — CSV 수신함(ProductSyncInboxService)용. */
    public StagingLoadResponse load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "파일이 없습니다: " + path.getFileName());
        }
        return load(path.getFileName().toString(), () -> Files.newInputStream(path));
    }

    @FunctionalInterface
    interface InputSource {
        InputStream open() throws IOException;
    }

    private StagingLoadResponse load(String filename, InputSource source) {
        if (loadService.isLoading()) {
            throw new CustomException(ErrorCode.STATE_CONFLICT, "제품 적재·반영이 진행 중입니다. 끝난 뒤 다시 올려 주세요.");
        }

        Parsed parsed = parse(read(source));
        LocalDateTime now = LocalDateTime.now();
        txTemplate.executeWithoutResult(status -> {
            stagingRepository.truncate();
            stagingRepository.insertAll(parsed.rows(), now);
        });

        log.info("[SYNC] 복제 테이블 채움 — file={} 행 {} · 중복 건너뜀 {} · master_id 없음 {} · 파일 hash 와 다름 {}",
                filename, parsed.rows().size(), parsed.duplicateCount(), parsed.missingIdCount(), parsed.hashMismatchCount());
        return new StagingLoadResponse(parsed.rows().size(), parsed.duplicateCount(), parsed.missingIdCount(),
                parsed.hashMismatchCount(), parsed.hashMismatchSamples());
    }

    /** CSV 레코드 → 복제 테이블 행. 순수 함수(테스트용으로 열어 둠). */
    static Parsed parse(List<CsvReader.Record> records) {
        if (records.isEmpty()) throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "헤더 행이 없습니다.");
        Map<String, Integer> header = new HashMap<>();
        List<String> names = records.get(0).fields();
        for (int i = 0; i < names.size(); i++) header.putIfAbsent(names.get(i).trim().toLowerCase(Locale.ROOT), i);
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "필수 열이 없습니다: " + String.join(", ", missing));
        }
        Integer fileHashCol = header.get("content_hash");
        Integer chipsCol = header.get(ProductStagingRepository.EXTRA_CATEGORIES);

        List<Map<String, String>> rows = new ArrayList<>(records.size());
        Set<String> seen = new HashSet<>();
        int duplicates = 0, missingId = 0, mismatches = 0;
        List<String> samples = new ArrayList<>();

        for (CsvReader.Record rec : records.subList(1, records.size())) {
            String masterId = rec.get(header.get("master_id")).trim();
            if (masterId.isEmpty()) { missingId++; continue; }
            if (!seen.add(masterId)) { duplicates++; continue; }

            Map<String, String> row = new HashMap<>();
            row.put("master_id", masterId);
            for (String c : ContentHashCalculator.HASH_COLUMNS) row.put(c, rec.get(header.get(c)));   // hash 와 같은 문자열 그대로(trim 하지 않음)
            String hash = ContentHashCalculator.hash(row);
            row.put("content_hash", hash);
            if (chipsCol != null) row.put(ProductStagingRepository.EXTRA_CATEGORIES, normalizeChips(rec.get(chipsCol), row.get("category")));

            if (fileHashCol != null) {
                String fileHash = rec.get(fileHashCol).trim();
                if (!fileHash.isEmpty() && !fileHash.equals(hash)) {
                    mismatches++;
                    if (samples.size() < SAMPLE_LIMIT) samples.add(masterId);
                }
            }
            rows.add(row);
        }
        return new Parsed(rows, duplicates, missingId, mismatches, samples);
    }

    /**
     * 칩 칸 정리 — 「|·,·;·공백」으로 나누고, 3자리 코드는 앞에 0 을 붙이고(엑셀이 뺀 0), 소속 중분류와 같은 코드는 빼고,
     * 중복을 없애 정렬해 "|" 로 잇는다. 칸이 비면 "". 반영 때 지금 칩과 비교하려고 늘 같은 모양으로 맞춘다.
     * 없는 코드·3개 이상은 여기서 거르지 않는다 — 반영 때 그 제품만 실패로 남는다(CSV 적재와 같음).
     */
    static String normalizeChips(String raw, String primaryCategory) {
        String primary = primaryCategory == null ? "" : code(primaryCategory);
        TreeSet<String> codes = new TreeSet<>();
        for (String token : (raw == null ? "" : raw).trim().split(SPLIT)) {
            if (token.isEmpty()) continue;
            String c = code(token);
            if (!c.equals(primary)) codes.add(c);
        }
        return String.join("|", codes);
    }

    private static String code(String raw) {
        String s = raw.trim();
        if (s.length() == 3 && s.chars().allMatch(Character::isDigit)) return "0" + s;
        return s;
    }

    /** UTF-8 로만 읽는다 — 엑셀 기본 저장(CP949)이면 한글이 깨지므로 400. */
    private static List<CsvReader.Record> read(InputSource source) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader r = new BufferedReader(new InputStreamReader(source.open(), decoder))) {
            return CsvReader.read(r);
        } catch (CharacterCodingException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "UTF-8 로 저장한 CSV 만 받습니다.");
        } catch (IOException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일을 읽을 수 없습니다.");
        }
    }
}