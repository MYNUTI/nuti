package com.example.nutriuniv.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GET /products/{productId} 출력 — 제품 결과 화면 (기능명세서 2.3·5.1 공용, API 명세 「기존 수정」).
 * <p>명세 블록: status · product · nutrition · grade · topReason · appliedGoal · saved.
 * 그 아래 1차 웹이 쓰던 필드들은 화면 전환이 끝날 때까지 유지한다(deprecated). 이름이 겹치는 {@code grade} 만 명세대로 객체로 바뀌었다.
 */
@Getter
@Builder
public class ProductDetailResponse {

    // ── 2차 결과 화면 (API 명세) ──────────────────────────────────────────────────

    private String status;                  // ANALYZED | INSUFFICIENT
    private ProductSummary product;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private NutritionFacts nutrition;       // 영양정보 행이 없으면 null, 있으면 값 8종(식이섬유 null 가능)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private GradeBadge grade;               // INSUFFICIENT 이면 없음. (1차의 문자열 grade 를 대체)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String topReason;               // 감점 요인 1개, 완성 문장

    private String appliedGoal;             // GENERAL | WEIGHT_LOSS | MUSCLE_GAIN
    private boolean saved;                  // 소유자(로그인·익명) 기준 저장 여부

    // ── 1차 웹 호환 (deprecated — 2차 화면 전환 후 제거) ──────────────────────────

    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal nutritionScore;
    private int viewCount;
    private boolean isFavorited;            // = saved
    private Double scoreRankPercent;
    private BrandInfo brand;
    private CategoryInfo category;
    private NutrientInfo nutrients;         // = nutrition + servingSize
    private CoupangInfo coupang;
    private PnsInfo pns;                    // 점수 미계산 상품이면 null
    private NutrientBounds nutrientBounds;  // 영양소 바 차트 기준값

    @Getter
    @Builder
    public static class BrandInfo {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class NutrientInfo {
        private String servingSize;
        private BigDecimal calories;
        private BigDecimal carbohydrate;
        private BigDecimal sugar;
        private BigDecimal protein;
        private BigDecimal fat;
        private BigDecimal saturatedFat;
        private BigDecimal transFat;
        private BigDecimal cholesterol;
        private BigDecimal sodium;
    }

    @Getter
    @Builder
    public static class CoupangInfo {
        private String affiliateUrl;
        private String landingUrl;
        private Integer price;
        private Boolean isRocket;
        private Boolean isFreeShipping;
        private LocalDateTime lastSyncedAt;
    }

    /**
     * 영양소 바 차트용 기준값 (사용자 EER 기반 Nmin/Nmax).
     * 비로그인 시 EER 2000 기준으로 계산.
     * null 값은 해당 영양소에 Nmin 또는 Nmax 없음을 의미.
     */
    @Getter
    @Builder
    public static class NutrientBounds {
        private double eerUsed;          // 계산에 사용된 EER (kcal)
        private double mealRatio;        // 적용된 meal_ratio (0.1 or 0.3)

        // 열량: 0 ~ EER×mealRatio
        private double caloriesMax;

        // 탄수화물: Nmin ~ Nmax
        private double carbMin;
        private double carbMax;

        // 단백질: Nmin ~ Nmax
        private double proteinMin;
        private double proteinMax;

        // 지방: Nmin ~ Nmax
        private double fatMin;
        private double fatMax;

        // 당류: 0 ~ Nmax
        private double sugarMax;

        // 포화지방: 0 ~ Nmax
        private double saturatedFatMax;

        // 트랜스지방: 0 ~ Nmax
        private double transFatMax;

        // 콜레스테롤: 0 ~ 300×mealRatio
        private double cholesterolMax;

        // 나트륨: 0 ~ Nmax (2300×mealRatio)
        private double sodiumMax;

        // 식이섬유: 0 ~ Nmin (목표값)
        private double fiberTarget;
    }

    @Getter
    @Builder
    public static class PnsInfo {
        private BigDecimal score;            // 0~100 정규화 점수
        private String grade;                // A~E
        private BigDecimal percentile;       // 카테고리 내 백분위 (0~100, 클수록 좋음) — 즉석 계산이면 null
        private BigDecimal topPercent;       // 상위 X% (= 100 - percentile)
        private Long parentCategoryId;       // 대분류 ID
        private String parentCategoryName;   // 대분류 이름 (예: "음료류")
        private int categoryTotal;           // 대분류 안 활성 상품 수
        private int eerBand;                 // 사용된 EER 구간 (1·2차 2000 고정)
    }
}
