package com.example.nutriuniv.domain.grade.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.grade.dto.GradeGuideResponse;
import com.example.nutriuniv.domain.grade.dto.GradeReasonResponse;
import com.example.nutriuniv.domain.grade.service.GradeGuideService;
import com.example.nutriuniv.domain.grade.service.GradeReasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Grade", description = "등급 산정 기준 안내·등급 근거 (기능명세서 4.1·5.2)")
@RestController
@RequiredArgsConstructor
public class GradeController {

    private final GradeGuideService gradeGuideService;
    private final GradeReasonService gradeReasonService;

    // GET /grades/guide
    @Operation(summary = "등급 산정 기준 안내 (명세 4.1)",
            description = "산정 단계·성분별 비중(현행 기준값 포함)·목표별 배점 차이·식이섬유 안내와 A~E 라벨·설명. " +
                    "goal 생략 시 내 목표(없으면 GENERAL). 재산출 뒤에는 클라 배포 없이 내용이 갱신됩니다.")
    @GetMapping("/grades/guide")
    public ResponseEntity<CommonResponse<GradeGuideResponse>> guide(
            Actor actor,
            @Parameter(description = "GENERAL | WEIGHT_LOSS | MUSCLE_GAIN (선택)") @RequestParam(required = false) String goal) {
        return ResponseEntity.ok(CommonResponse.success(gradeGuideService.guide(goal, actor)));
    }

    // GET /products/{productId}/grade-reason
    @Operation(summary = "등급 근거 「왜 C인가요?」 (명세 5.2)",
            description = "감점 요인의 실제 값(100g 기준)과 기준값, 그 목표에서 왜 중요한지 한 줄, 식이섬유 0 계산 안내, 산정 방법. " +
                    "영양정보 부족 제품은 409 GRADE_NOT_AVAILABLE, 없는·비활성 제품은 404. 분류 내 위치는 3차 전까지 제공하지 않습니다.")
    @GetMapping("/products/{productId}/grade-reason")
    public ResponseEntity<CommonResponse<GradeReasonResponse>> reason(
            Actor actor,
            @Parameter(description = "제품 ID") @PathVariable Long productId,
            @Parameter(description = "GENERAL | WEIGHT_LOSS | MUSCLE_GAIN (선택)") @RequestParam(required = false) String goal) {
        return ResponseEntity.ok(CommonResponse.success(gradeReasonService.reason(productId, goal, actor)));
    }
}
