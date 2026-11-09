package com.example.nutriuniv.domain.grade.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.grade.dto.GradeGuideResponse;
import com.example.nutriuniv.domain.grade.service.GradeGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Grade", description = "등급 산정 기준 안내")
@RestController
@RequiredArgsConstructor
public class GradeController {

    private final GradeGuideService gradeGuideService;

    // GET /grades/guide
    @Operation(summary = "등급 산정 기준 안내 (명세 4.1)",
            description = "산정 단계·성분별 비중·목표별 차이·식이섬유 안내와 A~E 라벨·설명. 기준선 수치는 주지 않습니다. " +
                    "goal 생략 시 내 목표(없으면 WEIGHT_LOSS). 재산출 뒤에는 클라 배포 없이 내용이 갱신됩니다.")
    @GetMapping("/grades/guide")
    public ResponseEntity<CommonResponse<GradeGuideResponse>> guide(
            Actor actor,
            @Parameter(description = "WEIGHT_LOSS | MUSCLE_GAIN (선택)") @RequestParam(required = false) String goal) {
        return ResponseEntity.ok(CommonResponse.success(gradeGuideService.guide(goal, actor)));
    }
}
