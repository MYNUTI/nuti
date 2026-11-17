package com.example.nutriuniv.domain.publicdata.repository;

import com.example.nutriuniv.domain.publicdata.client.PublicApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 원본 표(public_api_raw)를 필요한 필드만 뽑아 한 줄씩 읽는다 — 재계산용.
 * C002 108만 · I2852 188만 행이라 한꺼번에 올리지 않고 커서로 흘려 읽는다(PostgreSQL 은 트랜잭션 안 + fetchSize 일 때 커서를 쓴다).
 * 받은 순서(id)대로 읽는다 — 같은 키가 여러 번 나오면 나중 것이 이긴다.
 */
@Repository
public class PublicApiRawReader {

    private static final Pattern FIELD = Pattern.compile("[A-Za-z0-9_]+");
    private static final int FETCH_SIZE = 5_000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate readTx;

    public PublicApiRawReader(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setFetchSize(FETCH_SIZE);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /** api 의 모든 행을 fields 만 뽑아 consumer 에 넘긴다. 값은 문자열(없으면 null). */
    public void forEach(PublicApi api, List<String> fields, Consumer<Map<String, String>> consumer) {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < fields.size(); i++) {
            String f = fields.get(i);
            if (!FIELD.matcher(f).matches()) throw new IllegalArgumentException("필드 이름이 이상합니다: " + f);
            if (i > 0) sql.append(", ");
            sql.append("payload->>'").append(f).append('\'');
        }
        sql.append(" FROM public_api_raw WHERE api = ? ORDER BY id");
        readTx.executeWithoutResult(s -> jdbcTemplate.query(sql.toString(), rs -> {
            Map<String, String> row = new HashMap<>(fields.size() * 2);
            for (int i = 0; i < fields.size(); i++) row.put(fields.get(i), rs.getString(i + 1));
            consumer.accept(row);
        }, api.name()));
    }

    /** 모든 행을 리스트로. 작은 API(푸드QR · HACCP · C005 · I2570 · I0490)용. */
    public List<Map<String, String>> readAll(PublicApi api, List<String> fields) {
        List<Map<String, String>> out = new ArrayList<>();
        forEach(api, fields, out::add);
        return out;
    }

    /** 주소 둘째 낱말 목록(중복 없이) — 지명 목록용. */
    public Set<String> distinctSecondAddressWords(Collection<PublicApi> apis, String field) {
        if (!FIELD.matcher(field).matches()) throw new IllegalArgumentException("필드 이름이 이상합니다: " + field);
        List<String> names = apis.stream().map(Enum::name).toList();
        Set<String> out = new HashSet<>();
        String sql = "SELECT DISTINCT split_part(btrim(payload->>'" + field + "'), ' ', 2) FROM public_api_raw WHERE api = ANY (?)";
        readTx.executeWithoutResult(s -> jdbcTemplate.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setArray(1, con.createArrayOf("varchar", names.toArray()));
            return ps;
        }, rs -> {
            String v = rs.getString(1);
            if (v != null && !v.isBlank()) out.add(v);
        }));
        return out;
    }

    /** 지금 product 의 master_id → 분류 코드(없으면 null). */
    public Map<String, String> currentCategories() {
        Map<String, String> m = new HashMap<>();
        readTx.executeWithoutResult(s -> jdbcTemplate.query("""
                SELECT p.master_id, c.code FROM products p LEFT JOIN categories c ON c.id = p.category_id
                WHERE p.master_id IS NOT NULL
                """, rs -> { m.put(rs.getString(1), rs.getString(2)); }));
        return m;
    }
}