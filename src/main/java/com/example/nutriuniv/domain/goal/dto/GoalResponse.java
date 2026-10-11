package com.example.nutriuniv.domain.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** GET /me/goal 출력. 미설정도 GENERAL 로 정상 응답(404 아님). */
@Getter
@Builder
public class GoalResponse {

    private String goal;
    private String label;
    private boolean healthConsented;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime appliedAt;   // 미설정이면 null
}
