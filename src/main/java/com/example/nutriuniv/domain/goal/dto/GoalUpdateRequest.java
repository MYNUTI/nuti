package com.example.nutriuniv.domain.goal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PUT /me/goal 입력. recalcProductId 가 있으면 그 제품의 전/후 등급을 응답에 함께 준다. */
@Getter
@NoArgsConstructor
public class GoalUpdateRequest {

    @NotBlank(message = "goal은 필수입니다.")
    private String goal;          // GENERAL | WEIGHT_LOSS | MUSCLE_GAIN

    private Long recalcProductId; // 선택
}
