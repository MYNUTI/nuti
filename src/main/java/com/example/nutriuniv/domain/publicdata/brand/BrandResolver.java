package com.example.nutriuniv.domain.publicdata.brand;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 묶음 하나의 brand 칸 계산 (brand.md 「계산 순서」). AI 없이 조회표(BrandLookupTables) + 문자열 규칙(BrandNameRules)으로만 정한다.
 * <ul>
 *   <li>영양성분DB 행(source = nutri_db)용이다. 행이 아니라 묶음 단위로 정하고, 묶음 안 모든 행에 같은 값을 넣는다(호출하는 쪽)</li>
 *   <li>앞 단계에서 값이 정해지면 거기서 끝난다</li>
 *   <li>브랜드로 확인되지 않으면 null — 마스터의 「제조 ○○」는 넣지 않는다. 틀리면 「브랜드인데 null」 방향이어야 한다(없는 브랜드를 지어내지 않음)</li>
 * </ul>
 * 바코드 주인(표 4)은 묶음 전체 품목보고번호의 C005 바코드로 호출하는 쪽이 미리 정해 넘긴다(공공데이터 수집 단계에서 만든다). 모르면 null — 검사를 건너뛴다.
 */
@Component
public class BrandResolver {

    /** 이름표에서 이 이름표면 브랜드로 본다(정한 곳이 믿을 만할 때). */
    private static final Set<String> BRAND_LABELS = Set.of(BrandLookupTables.LABEL_BRAND, BrandLookupTables.LABEL_RETAILER);

    /**
     * 묶음 하나의 입력. 제품명 · 판매원 · 제조사 · 수입은 묶음 대표 행에서.
     * @param groupKey     묶음 키(16자리). 쿠팡 보정 · 11-12 고침 · 쿠팡 상품명을 찾는 키. 새 묶음이면 그 표들에는 없다
     * @param productName  FOOD_NM_KR
     * @param seller       SELLER_MANUFAC_NM (원문 그대로)
     * @param maker        MAKER_NM
     * @param importer     IMP_MANUFAC_NM
     * @param barcodeOwner 묶음 880 바코드의 주인 회사(표 4). 모르면 null
     */
    public record Input(String groupKey, String productName, String seller, String maker, String importer, String barcodeOwner) {}

    private final BrandLookupTables tables;
    private final BrandNameRules rules;

    /** 생성자가 둘이라(테스트용 아래) Spring 이 이것을 쓰도록 표시한다. */
    @Autowired
    public BrandResolver(BrandLookupTables tables) {
        this(tables, new BrandNameRules());
    }

    BrandResolver(BrandLookupTables tables, BrandNameRules rules) {
        this.tables = tables;
        this.rules = rules;
    }

    /** 조회표를 다 읽었는가 — false 면 resolve 가 예외를 던진다. */
    public boolean isReady() {
        return tables.isReady();
    }

    /** brand 칸 값. 브랜드로 확인되지 않으면 null. 조회표가 준비되지 않았으면 IllegalStateException(틀린 표로 조용히 비우지 않게). */
    public String resolve(Input in) {
        if (!tables.isReady()) {
            throw new IllegalStateException("브랜드 조회표가 준비되지 않았습니다: " + tables.problem());
        }
        String g = in.groupKey();

        // 1. 11-12 수동 고침 — 빈 값이나 「제조 ○○」면 null
        Optional<String> fixed = tables.fix1112(g);
        if (fixed.isPresent()) return BrandNameRules.nullIfMaker(fixed.get());

        // 2. 쿠팡 보정 — 고정값 그대로(합치기 안 거침)
        Optional<String> coupang = tables.coupangFix(g);
        if (coupang.isPresent()) return coupang.get();

        // 3. 제품명 첫 낱말 → 브랜드 사전 (메뉴 머리말 「피자_ …」이면 건너뜀)
        List<String> t = BrandNameRules.tokens(in.productName());
        String w = t.isEmpty() ? "" : BrandNameRules.compact(t.get(0));
        boolean menuHead = t.size() > 1 && t.get(1).equals("_");
        if (!menuHead) {
            Optional<String> b = tables.dictionaryLookup(w);
            if (b.isPresent()) return tables.merge2(b.get());
        }

        // 4. 회사 이름 고르기: 판매원 > 제조사 > 수입 → 정리 → 1단계 합치기
        String src, raw;
        if (notBlank(in.seller()))        { src = "판매원"; raw = in.seller(); }
        else if (notBlank(in.maker()))    { src = "제조사"; raw = in.maker(); }
        else if (notBlank(in.importer())) { src = "수입";   raw = in.importer(); }
        else return null;
        String c = rules.clean(raw);
        String v = c.isEmpty() ? raw.strip() : BrandNameRules.merge1(c);

        // 5. 하위 브랜드
        String unique = BrandNameRules.UNIQUE.get(w);
        if (unique != null) return tables.merge2(unique);
        if (BrandNameRules.SUB.getOrDefault(v, Set.of()).contains(w)) return tables.merge2(w);

        if (!src.equals("판매원")) v = BrandNameRules.stripForeign(v);   // 해외 법인 꼬리 2차 (제조사 · 수입만)
        if (v.equals("SPC삼립")) v = "삼립";                              // apply_v3 SAME — 이름표 키도 「삼립」

        // 6. 판매원은 이름표 · 바코드를 보지 않는다
        if (src.equals("판매원")) return tables.merge2(v);

        // 7. 제조사 · 수입: 바코드 주인 → 회사 이름표
        String owner = in.barcodeOwner();
        if (owner != null && !BrandNameRules.same(v, owner) && !BrandNameRules.affiliate(v, owner)) {
            return null;   // 남의 제품을 만든 회사
        }
        Optional<BrandLookupTables.CompanyLabel> label = tables.companyLabel(v);
        if (label.isEmpty() || !label.get().trusted()) return null;   // 표에 없는 새 회사 · 믿을 만하지 않은 판정
        String l = label.get().label();
        if (BRAND_LABELS.contains(l)) return tables.merge2(v);
        if (l.equals(BrandLookupTables.LABEL_BOTH)) {
            String k = BrandNameRules.core(v);
            if (k.length() >= 2 && (BrandNameRules.compact(in.productName()).contains(k)
                    || BrandNameRules.compact(tables.coupangName(g)).contains(k))) {
                return tables.merge2(v);
            }
            if (owner != null && BrandNameRules.same(v, owner)) return tables.merge2(v);
        }
        return null;   // 제조 전문 · 업소·원료 · 미확인 · 「둘 다」인데 자기 제품이라는 신호가 없음
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}