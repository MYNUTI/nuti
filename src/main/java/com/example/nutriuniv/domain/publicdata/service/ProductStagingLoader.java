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
import java.util.function.Consumer;

/**
 * 34컬럼 CSV(파이프라인 product_master.csv 형식)로 복제 테이블을 통째로 채운다.
 * 입력은 두 가지 — 관리자가 올린 파일(POST /admin/product-sync/staging), 파이프라인이 /data/csv 에 둔 파일(CSV 수신함).
 * <ul>
 *   <li>복제 테이블은 「지금 원천 전체」를 뜻한다 — 비우고 채우기를 한 트랜잭션으로(중간에 실패하면 이전 내용 그대로)</li>
 *   <li>파일은 한 레코드씩 읽어 {@value #INSERT_BATCH}행마다 넣는다 — 파일 전체를 메모리에 올리지 않는다(서버 힙이 작다).
 *       비우기 전에 한 번 훑어 인코딩 · 헤더만 확인한다(행은 붙잡지 않음)</li>
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
    static final int INSERT_BATCH = 1_000;
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

        checkHeader(source);   // 1차: 인코딩 · 헤더만 — 실패하면 복제 테이블은 그대로
        StagingWriter writer = new StagingWriter(LocalDateTime.now());
        txTemplate.executeWithoutResult(status -> {
            stagingRepository.truncate();
            stream(source, writer);
            writer.flush();
        });

        RowParser p = writer.parser;
        log.info("[SYNC] 복제 테이블 채움 — file={} 행 {} · 중복 건너뜀 {} · master_id 없음 {} · 파일 hash 와 다름 {}",
                filename, p.rows, p.duplicates, p.missingId, p.mismatches);
        return new StagingLoadResponse(p.rows, p.duplicates, p.missingId, p.mismatches, List.copyOf(p.samples));
    }

    /** 비우기 전에 한 번 훑는다 — UTF-8 이 아니거나 필수 열이 없으면 여기서 멈춘다. 행은 붙잡지 않는다. */
    private static void checkHeader(InputSource source) {
        boolean[] header = {false};
        stream(source, rec -> {
            if (header[0]) return;
            new RowParser(rec.fields());
            header[0] = true;
        });
        if (!header[0]) throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "헤더 행이 없습니다.");
    }

    /** 한 레코드씩 행으로 바꿔 {@link #INSERT_BATCH}행마다 넣는다. 호출자가 트랜잭션을 연다. */
    private final class StagingWriter implements Consumer<CsvReader.Record> {
        private final LocalDateTime at;
        private RowParser parser;
        private List<Map<String, String>> batch = new ArrayList<>(INSERT_BATCH);

        StagingWriter(LocalDateTime at) {
            this.at = at;
        }

        @Override
        public void accept(CsvReader.Record rec) {
            if (parser == null) {
                parser = new RowParser(rec.fields());
                return;
            }
            Map<String, String> row = parser.next(rec);
            if (row == null) return;
            batch.add(row);
            if (batch.size() >= INSERT_BATCH) flush();
        }

        void flush() {
            if (batch.isEmpty()) return;
            stagingRepository.insertAll(batch, at);
            batch = new ArrayList<>(INSERT_BATCH);
        }
    }

    /** CSV 레코드 → 복제 테이블 행. 순수 함수(테스트용으로 열어 둠). 서버는 {@link RowParser} 로 한 레코드씩 바꾼다. */
    static Parsed parse(List<CsvReader.Record> records) {
        if (records.isEmpty()) throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "헤더 행이 없습니다.");
        RowParser parser = new RowParser(records.get(0).fields());
        List<Map<String, String>> rows = new ArrayList<>(records.size());
        for (CsvReader.Record rec : records.subList(1, records.size())) {
            Map<String, String> row = parser.next(rec);
            if (row != null) rows.add(row);
        }
        return new Parsed(rows, parser.duplicates, parser.missingId, parser.mismatches, List.copyOf(parser.samples));
    }

    /** 헤더를 확인하고 레코드를 복제 테이블 행으로 바꾼다 — 중복 · 빈 master_id · 파일 hash 와 다른 행을 센다. */
    static final class RowParser {
        private final Map<String, Integer> header = new HashMap<>();
        private final Integer fileHashCol;
        private final Integer chipsCol;
        private final Set<String> seen = new HashSet<>();
        int rows, duplicates, missingId, mismatches;
        final List<String> samples = new ArrayList<>();

        RowParser(List<String> names) {
            for (int i = 0; i < names.size(); i++) header.putIfAbsent(names.get(i).trim().toLowerCase(Locale.ROOT), i);
            List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
            if (!missing.isEmpty()) {
                throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "필수 열이 없습니다: " + String.join(", ", missing));
            }
            fileHashCol = header.get("content_hash");
            chipsCol = header.get(ProductStagingRepository.EXTRA_CATEGORIES);
        }

        /** 넣을 행. 건너뛰는 행(빈 master_id · 이미 나온 master_id)이면 null. */
        Map<String, String> next(CsvReader.Record rec) {
            String masterId = rec.get(header.get("master_id")).trim();
            if (masterId.isEmpty()) { missingId++; return null; }
            if (!seen.add(masterId)) { duplicates++; return null; }

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
            rows++;
            return row;
        }
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

    /** UTF-8 로만 읽는다 — 엑셀 기본 저장(CP949)이면 한글이 깨지므로 400. 레코드는 읽는 대로 넘긴다. */
    private static void stream(InputSource source, Consumer<CsvReader.Record> sink) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader r = new BufferedReader(new InputStreamReader(source.open(), decoder))) {
            CsvReader.forEach(r, sink);
        } catch (CharacterCodingException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "UTF-8 로 저장한 CSV 만 받습니다.");
        } catch (IOException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "파일을 읽을 수 없습니다.");
        }
    }
}