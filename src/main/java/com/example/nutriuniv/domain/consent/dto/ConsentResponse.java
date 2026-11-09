package com.example.nutriuniv.domain.consent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** POST /onboarding/consent 출력 — 동의 시각. 익명 ID 는 v3 부터 기기가 만들어 여기서 돌려주지 않는다. */
@Getter
@Builder
public class ConsentResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime agreedAt;

    public static ConsentResponse of(LocalDateTime agreedAt) {
        return ConsentResponse.builder().agreedAt(agreedAt).build();
    }
}
