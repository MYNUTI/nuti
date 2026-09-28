package com.example.nutriuniv.domain.user.controller;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.security.UserPrincipal;
import com.example.nutriuniv.domain.user.dto.*;
import com.example.nutriuniv.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "유저 API")
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회",
            description = "id·email·nickname·provider·createdAt(+role). name·gender·birthDate 는 2차부터 수집하지 않아 응답에서 빠졌습니다.")
    @GetMapping
    public ResponseEntity<CommonResponse<UserResponse>> getMe(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(CommonResponse.success(userService.getMe(principal.getId())));
    }

    @Operation(summary = "회원정보 수정",
            description = "수정 가능한 항목은 email·nickname 만. 둘 다 없으면 400.")
    @PatchMapping
    public ResponseEntity<CommonResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(userService.updateMe(principal.getId(), request)));
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping
    public ResponseEntity<CommonResponse<Void>> deleteMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String reason) {
        userService.deleteMe(principal.getId(), reason);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "영양정보 조회")
    @GetMapping("/nutrition")
    public ResponseEntity<CommonResponse<UserNutritionResponse>> getNutrition(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(CommonResponse.success(userService.getNutrition(principal.getId())));
    }

    @Operation(summary = "영양정보 등록")
    @PostMapping("/nutrition")
    public ResponseEntity<CommonResponse<Void>> createNutrition(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserNutritionRequest request) {
        userService.createNutrition(principal.getId(), request);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "영양정보 수정")
    @PutMapping("/nutrition")
    public ResponseEntity<CommonResponse<Void>> updateNutrition(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserNutritionRequest request) {
        userService.updateNutrition(principal.getId(), request);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}