package com.example.nutriuniv.domain.consent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** POST /onboarding/consent 출력 — 여기서 받은 anonymousId 를 클라이언트가 localStorage 에 보관한다. */
@Getter
@Builder
public class ConsentResponse {

    private String anonymousId;   // 로그인 상태에서 동의한 경우 헤더 값 그대로(없으면 null)

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime agreedAt;

    public static ConsentResponse of(String anonymousId, LocalDateTime agreedAt) {
        return ConsentResponse.builder().anonymousId(anonymousId).agreedAt(agreedAt).build();
    }
}
