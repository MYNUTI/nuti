package com.example.nutriuniv.domain.goal.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.goal.dto.GoalResponse;
import com.example.nutriuniv.domain.goal.dto.GoalUpdateRequest;
import com.example.nutriuniv.domain.goal.dto.GoalUpdateResponse;
import com.example.nutriuniv.domain.goal.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Goal", description = "목표 API")
@RestController
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    // GET /me/goal
    @Operation(summary = "내 목표 조회 (기능명세서 2.4)",
            description = "X-Anonymous-Id 또는 Bearer. 없거나 미설정이면 GENERAL로 정상 응답합니다(404 아님).")
    @GetMapping("/me/goal")
    public ResponseEntity<CommonResponse<GoalResponse>> getGoal(Actor actor) {
        return ResponseEntity.ok(CommonResponse.success(goalService.getGoal(actor)));
    }

    // PUT /me/goal
    @Operation(summary = "목표 설정 + 재계산 (기능명세서 2.4)",
            description = "X-Anonymous-Id 또는 Bearer 필수(없으면 403 CONSENT_REQUIRED). " +
                    "WEIGHT_LOSS·MUSCLE_GAIN은 건강정보 동의 선행(미동의 403 HEALTH_CONSENT_REQUIRED). 허용값 외 400. " +
                    "recalcProductId가 있으면 그 제품의 전/후 등급과 이유를 한 응답에 담습니다(등급 없는 제품이면 recalculation=null). " +
                    "키·몸무게·나이·활동량은 받지 않습니다.")
    @PutMapping("/me/goal")
    public ResponseEntity<CommonResponse<GoalUpdateResponse>> setGoal(
            Actor actor,
            @Valid @RequestBody GoalUpdateRequest request) {

        return ResponseEntity.ok(CommonResponse.success(goalService.setGoal(actor, request)));
    }
}
