package com.example.nutriuniv.domain.home.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.Actor;
import com.example.nutriuniv.domain.home.dto.HomeResponse;
import com.example.nutriuniv.domain.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 (v3 SRCH_006)")
@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 (v3 SRCH_006)",
            description = "최근 본 제품 수(제품 기준), 본 제품의 목표별 등급 분포(A~E·NONE)와 A+B 개수, 내 분석 요청 수, " +
                    "순위가 열린 칩이 있는 대분류, 판매처별 제품 수. 로그인·기기 ID 가 없으면 개인 수치는 0.")
    @GetMapping("/home")
    public ResponseEntity<CommonResponse<HomeResponse>> home(
            Actor actor,
            @Parameter(description = "WEIGHT_LOSS(기본) | MUSCLE_GAIN") @RequestParam(required = false) String goal,
            @Parameter(description = "하루 칼로리 구간 (1500·2000·2500·3000)") @RequestParam(required = false) Integer kcalBand) {
        return ResponseEntity.ok(CommonResponse.success(homeService.home(goal, kcalBand, actor)));
    }
}
