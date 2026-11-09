package com.example.nutriuniv.domain.product.enums;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.grade.calc.NutrientEvaluator.Fact;

/**
 * 검색 · 랭킹 정렬 5가지 (v3 SRCH_001 · SRCH_007) — 두 화면이 같은 목록 · 같은 순서.
 * <ul>
 *   <li>같은 점수면 이름순. 등급 없음은 맨 뒤</li>
 *   <li>영양소 정렬은 100g(음료 100mL)당 값. 그 값이 없거나 등급이 없으면 맨 뒤, 값이 같으면 점수 → 이름순</li>
 *   <li>인기순: 행동 가중합이 모이기 전(임시)은 쿠팡에 연결된 제품 먼저, 그 안에서 점수순</li>
 * </ul>
 */
public enum ListSort {
    SCORE(null, null, "점수 높은 순"),
    POPULAR(null, null, "인기순"),
    CALORIES_LOW(Fact.CALORIES, "ASC", "칼로리 낮은 순"),
    PROTEIN_HIGH(Fact.PROTEIN, "DESC", "단백질 많은 순"),
    SUGAR_LOW(Fact.SUGAR, "ASC", "당류 적은 순");

    private final Fact nutrient;
    private final String direction;
    private final String label;

    ListSort(Fact nutrient, String direction, String label) {
        this.nutrient = nutrient;
        this.direction = direction;
        this.label = label;
    }

    public Fact nutrient() { return nutrient; }
    public String label() { return label; }

    /** 영양소 정렬인가 — 카드 태그 한 줄을 그 값으로 바꾼다. */
    public boolean byNutrient() { return nutrient != null; }

    /** ORDER BY 절. 별칭: p(products) · pn(product_nutrients) · pg(product_grades) · cl(coupang_links). */
    public String orderBy() {
        String tail = "pg.score DESC, p.name, p.id";
        return switch (this) {
            case SCORE -> "(pg.score IS NULL), " + tail;
            case POPULAR -> "(pg.score IS NULL), (cl.link_status = 'LINKED' AND COALESCE(cl.affiliate_url, '') <> '') DESC, " + tail;
            default -> {
                String col = "pn." + column(nutrient);
                yield "(pg.score IS NULL OR " + col + " IS NULL), " + col + " " + direction + ", " + tail;
            }
        };
    }

    static String column(Fact f) {
        return switch (f) {
            case CALORIES -> "calories_per_100g";
            case PROTEIN -> "protein_per_100g";
            case SUGAR -> "sugar_per_100g";
            default -> throw new IllegalArgumentException(f.name());
        };
    }

    public static ListSort from(String raw) {
        if (raw == null || raw.isBlank()) return SCORE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_QUERY_PARAM,
                    "sort는 SCORE·POPULAR·CALORIES_LOW·PROTEIN_HIGH·SUGAR_LOW 중 하나여야 합니다.");
        }
    }
}
