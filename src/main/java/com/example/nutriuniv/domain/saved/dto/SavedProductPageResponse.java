package com.example.nutriuniv.domain.saved.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** GET /me/saved-products 출력. INSUFFICIENT 제품은 grade·label 이 null 로 내려간다(등급 없이 표시). */
@Getter
@Builder
public class SavedProductPageResponse {

    private List<Item> items;
    private long totalCount;
    private boolean hasNext;

    @Getter
    @Builder
    public static class Item {
        private Long productId;
        private String name;
        private String imageUrl;
        private String status;     // ANALYZED | INSUFFICIENT
        private String grade;      // A~E, 없으면 null
        private String label;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime savedAt;
    }
}
