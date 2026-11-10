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

    @Operation(summary = "계정 삭제 (v3 ACCT_008)",
            description = "되돌릴 수 없습니다. 계정 정보·계정 목표·즐겨찾기·최근 본 제품·동의 이력·몸 정보·로그인 토큰을 지웁니다. " +
                    "분석 요청·제보는 계정 연결만 끊고 남깁니다(운영 대기열에서 계속 처리). 실패하면 계정을 그대로 둡니다. " +
                    "같은 회사 계정으로 다시 로그인하면 새 가입입니다. 기기는 처음 방문 상태로 돌아가야 합니다(기기 ID 새로 만들기 — 클라이언트).")
    @DeleteMapping
    public ResponseEntity<CommonResponse<Void>> deleteMe(@AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteMe(principal.getId());
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
