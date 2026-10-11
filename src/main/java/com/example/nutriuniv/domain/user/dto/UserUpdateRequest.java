package com.example.nutriuniv.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /users/me 입력 — 이메일 외 프로필은 수집하지 않으므로 수정 가능한 항목도 이메일·닉네임만. */
@Getter
@NoArgsConstructor
public class UserUpdateRequest {

    @Pattern(
            regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$",
            message = "올바른 이메일 형식이 아닙니다."
    )
    private String email;

    @Size(min = 1, max = 50, message = "닉네임은 1~50자여야 합니다.")
    private String nickname;
}
