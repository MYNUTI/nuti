package com.example.nutriuniv.domain.saved.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/** POST → {saved:true, savedCount} / DELETE → {saved:false}. 둘 다 멱등(200). */
@Getter
@Builder
public class SaveResultResponse {

    private boolean saved;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long savedCount;

    public static SaveResultResponse saved(long savedCount) {
        return SaveResultResponse.builder().saved(true).savedCount(savedCount).build();
    }

    public static SaveResultResponse removed() {
        return SaveResultResponse.builder().saved(false).build();
    }
}
