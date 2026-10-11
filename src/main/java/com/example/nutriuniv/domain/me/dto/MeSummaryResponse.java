package com.example.nutriuniv.domain.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /me/summary 출력 (API 명세). 비로그인은 profile=null(클라 「둘러보는 중이에요」). recordCount 는 익명 ID 기준도 집계한다.
 * notification 은 3차 보류로 없다.
 */
@Getter
@Builder
public class MeSummaryResponse {

    @JsonProperty("isLoggedIn")
    private boolean loggedIn;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Profile profile;

    private Goal goal;
    private long recordCount;               // 저장한 제품 수 (로그인 화면 「기록 N개」와 같은 값)
    private Contribution contribution;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String footerNotice;            // 비로그인: 「로그인하면 기기가 바뀌어도 기록이 유지돼요」

    @Getter
    @Builder
    public static class Profile {
        private String nickname;
        private String email;
        private String provider;
    }

    @Getter
    @Builder
    public static class Goal {
        private String code;
        private String label;
    }

    @Getter
    @Builder
    public static class Contribution {
        private long requestCount;          // 내가 접수(자동 등록 포함)한 분석 대기 건수
        private long publishedCount;        // 그중 처리 완료(DONE)된 항목 수
    }
}
