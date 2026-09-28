package com.example.nutriuniv.domain.support.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /support/inquiries 입력 — content 필수(1000자 이하), category·contactEmail 선택. */
@Getter
@NoArgsConstructor
public class InquiryCreateRequest {
    private String category;
    private String content;
    private String contactEmail;
}
