package com.example.nutriuniv.domain.user.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.user.dto.*;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    // ── GET /users/me ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = getUser(userId);
        return UserResponse.from(user);
    }

    // ── PATCH /users/me ───────────────────────────────────────────────────────────

    /** 수정 가능한 프로필은 이메일·닉네임만 (name·gender·birthDate 는 2차부터 미수집). */
    @Transactional
    public UserResponse updateMe(Long userId, UserUpdateRequest request) {
        if (request.getEmail() == null && request.getNickname() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "수정할 항목이 없습니다.");
        }
        User user = getUser(userId);
        user.updateProfile(request.getEmail(), request.getNickname());
        return UserResponse.from(user);
    }

    // ── DELETE /users/me (ACCT_008) ───────────────────────────────────────────────

    @Transactional
    public void deleteMe(Long userId) {
        accountDeletionService.delete(userId);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────────

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
