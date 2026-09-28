package com.example.nutriuniv.domain.search.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PopularKeywordResponse {
    private int rank;
    private String keyword;
}
