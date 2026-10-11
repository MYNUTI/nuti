package com.example.nutriuniv.domain.link.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /links/resolve/{resolveId}/feedback 입력 — confirmed 필수, 「아니에요」일 때 바로잡은 제품 ID 선택. */
@Getter
@NoArgsConstructor
public class LinkFeedbackRequest {
    private Boolean confirmed;
    private Long correctedProductId;
}
