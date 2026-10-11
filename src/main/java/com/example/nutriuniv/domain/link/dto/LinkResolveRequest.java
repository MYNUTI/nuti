package com.example.nutriuniv.domain.link.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /links/resolve 입력. */
@Getter
@NoArgsConstructor
public class LinkResolveRequest {
    private String url;
}
