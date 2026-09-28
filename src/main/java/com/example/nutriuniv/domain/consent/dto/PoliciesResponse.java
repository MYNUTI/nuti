package com.example.nutriuniv.domain.consent.dto;

import com.example.nutriuniv.domain.consent.entity.Policy;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** GET /meta/policies 출력 — bootstrap 의 currentPolicyVersion 과 비교해 행동 시점에 재동의를 유도한다. 정책 행이 없으면 null. */
@Getter
@Builder
public class PoliciesResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Item privacy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Item terms;

    @Getter
    @Builder
    public static class Item {
        private String version;
        private String url;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate updatedAt;        // 시행일(effective_from)

        public static Item from(Policy p) {
            return Item.builder().version(p.getVersion()).url(p.getUrl()).updatedAt(p.getEffectiveFrom()).build();
        }
    }
}
