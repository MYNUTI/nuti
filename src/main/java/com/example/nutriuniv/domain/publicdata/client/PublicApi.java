package com.example.nutriuniv.domain.publicdata.client;

/**
 * 34컬럼 계산에 쓰는 공공 API 13개 (sync.md 「API 목록」). 행 수 · 호출 수는 2026-10-19~28 전량 수집 실측.
 * <ul>
 *   <li>pageSize — 한 번에 받는 행 수. 식품안전나라는 최대 1,000(ERROR-336)</li>
 *   <li>condition — 쓰는 조건. CHNG_DT(증분) · EVL_YR(연도) · NONE(전량만)</li>
 *   <li>dailyLimit — 하루 호출 한도. 식품안전나라는 서비스 안내 페이지 표기(실제로 하루 한도처럼 걸림), 모르는 것은 1,000.
 *       공공데이터포털은 안내 페이지에 없어 1,000 으로 두고 publicdata.datagokr.daily-limit 로 바꾼다(마이페이지 인증키 정보 확인 필요)</li>
 *   <li>totalUntrusted — C005 는 totalCount 가 요청 구간 크기를 그대로 돌려줘 믿을 수 없다. 빈 페이지가 나올 때까지 받는다</li>
 * </ul>
 */
public enum PublicApi {

    // ── 공공데이터포털 (apis.data.go.kr) — 전량만 ──
    NUTRI_DB("영양성분DB", Provider.DATA_GO_KR, "1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02", 500, Condition.NONE, 1_000, false),
    FOODQR_LIST("푸드QR 목록", Provider.DATA_GO_KR, "1471000/FoodQrInfoService01/getFoodQrProdList01", 100, Condition.NONE, 1_000, false),
    FOODQR_NUTRITION("푸드QR 영양", Provider.DATA_GO_KR, "1471000/FoodQrInfoService01/getFoodQrProdNsd01", 100, Condition.NONE, 1_000, false),
    FOODQR_MAKER("푸드QR 제조", Provider.DATA_GO_KR, "1471000/FoodQrInfoService01/getFoodQrProdMnfInfo01", 100, Condition.NONE, 1_000, false),
    HACCP("HACCP 포장표기", Provider.DATA_GO_KR, "B553748/CertImgListServiceV3/getCertImgListServiceV3", 100, Condition.NONE, 1_000, false),

    // ── 식품안전나라 (openapi.foodsafetykorea.go.kr) ──
    C005("C005 바코드연계제품", Provider.FOOD_SAFETY, "C005", 1_000, Condition.CHNG_DT, 500, true),
    I2570("I2570 유통바코드", Provider.FOOD_SAFETY, "I2570", 1_000, Condition.CHNG_DT, 500, false),
    C002("C002 품목제조보고 원재료", Provider.FOOD_SAFETY, "C002", 1_000, Condition.CHNG_DT, 1_000, false),
    C006("C006 축산물 원재료", Provider.FOOD_SAFETY, "C006", 1_000, Condition.CHNG_DT, 1_000, false),
    I2852("I2852 생산중단", Provider.FOOD_SAFETY, "I2852", 1_000, Condition.CHNG_DT, 1_000, false),
    I0490("I0490 회수·판매중지", Provider.FOOD_SAFETY, "I0490", 1_000, Condition.NONE, 2_000, false),
    I0300("I0300 생산실적", Provider.FOOD_SAFETY, "I0300", 1_000, Condition.EVL_YR, 1_000, false),
    I1420("I1420 축산물 생산실적", Provider.FOOD_SAFETY, "I1420", 1_000, Condition.EVL_YR, 1_000, false);

    public enum Provider { DATA_GO_KR, FOOD_SAFETY }

    public enum Condition {
        NONE(null), CHNG_DT("CHNG_DT"), EVL_YR("EVL_YR");

        private final String param;

        Condition(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }
    }

    private final String label;
    private final Provider provider;
    private final String path;
    private final int pageSize;
    private final Condition condition;
    private final int dailyLimit;
    private final boolean totalUntrusted;

    PublicApi(String label, Provider provider, String path, int pageSize, Condition condition, int dailyLimit, boolean totalUntrusted) {
        this.label = label;
        this.provider = provider;
        this.path = path;
        this.pageSize = pageSize;
        this.condition = condition;
        this.dailyLimit = dailyLimit;
        this.totalUntrusted = totalUntrusted;
    }

    public String label()            { return label; }
    public Provider provider()       { return provider; }
    /** 공공데이터포털: 기관/서비스/오퍼레이션. 식품안전나라: 서비스 ID. */
    public String path()             { return path; }
    public int pageSize()            { return pageSize; }
    public Condition condition()     { return condition; }
    public int dailyLimit()          { return dailyLimit; }
    public boolean totalUntrusted()  { return totalUntrusted; }
}