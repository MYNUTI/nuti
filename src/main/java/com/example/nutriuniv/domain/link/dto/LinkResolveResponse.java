package com.example.nutriuniv.domain.link.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/** POST /links/resolve 출력 — 미지원·매칭 실패도 200 + matched=null (클라이언트는 parsedTitle 로 검색 폴백). */
@Getter
@Builder
public class LinkResolveResponse {

    private String resolveId;
    private String sourceType;              // COUPANG | UNKNOWN

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String parsedTitle;             // 페이지에서 읽은 제품명(정리 후) — 검색 폴백 질의로 쓴다

    private boolean cached;                 // 24시간 캐시에서 나온 결과인가

    private Matched matched;                // null = 못 찾음

    @Getter
    @Builder
    public static class Matched {
        private Long productId;
        private String name;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String imageUrl;

        private String status;              // ANALYZED | INSUFFICIENT

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String grade;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String gradeLabel;

        private double confidence;          // 0~1. 쿠팡 상품 ID 일치·사용자 교정 1.0, 이름 포함 0.9, 유사도는 그 값
        private String method;              // FEEDBACK | COUPANG_ID | EXACT_NAME | SIMILAR
    }
}
