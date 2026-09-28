package com.example.nutriuniv.domain.user.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.domain.auth.repository.AuthTokenRepository;
import com.example.nutriuniv.domain.user.dto.*;
import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.entity.UserNutrition;
import com.example.nutriuniv.domain.user.repository.UserNutritionRepository;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserNutritionRepository userNutritionRepository;
    private final AuthTokenRepository authTokenRepository;

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

    // ── DELETE /users/me ──────────────────────────────────────────────────────────

    @Transactional
    public void deleteMe(Long userId, String reason) {
        User user = getUser(userId);
        authTokenRepository.deleteByUserId(userId);
        // deactivate() 내부에서 oauthId를 "DELETED_{id}"로 초기화함
        // → 같은 소셜 계정으로 재가입 시 DB unique constraint 충돌 방지
        user.deactivate();
        // TODO: user_withdraw_reasons 저장, user_favorites 삭제
    }

    // ── GET /users/me/nutrition ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserNutritionResponse getNutrition(Long userId) {
        UserNutrition nutrition = userNutritionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "영양정보가 등록되지 않았습니다."));
        return UserNutritionResponse.from(nutrition);
    }

    // ── POST /users/me/nutrition ──────────────────────────────────────────────────

    @Transactional
    public void createNutrition(Long userId, UserNutritionRequest request) {
        if (userNutritionRepository.existsByUserId(userId)) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE, "이미 영양정보가 등록되었습니다.");
        }
        validateNutritionRequest(request);
        User user = getUser(userId);
        userNutritionRepository.save(UserNutrition.create(
                user, request.getHeight(), request.getWeight(),
                request.getBodyFatRate(), request.getSkeletalMuscleMass(),
                request.getDietPurpose(), request.getActivityType(),
                request.getWeeklyExerciseCount(), request.getExerciseIntensity(),
                request.getDailyMealCount(), request.getDailySnackCount()
        ));
    }

    // ── PUT /users/me/nutrition ───────────────────────────────────────────────────

    @Transactional
    public void updateNutrition(Long userId, UserNutritionRequest request) {
        validateNutritionRequest(request);
        UserNutrition nutrition = userNutritionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "영양정보가 등록되지 않았습니다."));
        nutrition.update(
                request.getHeight(), request.getWeight(),
                request.getBodyFatRate(), request.getSkeletalMuscleMass(),
                request.getDietPurpose(), request.getActivityType(),
                request.getWeeklyExerciseCount(), request.getExerciseIntensity(),
                request.getDailyMealCount(), request.getDailySnackCount()
        );
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────────

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateNutritionRequest(UserNutritionRequest req) {
        if (req.getHeight() == null || req.getHeight().signum() <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "height는 0보다 커야 합니다.");
        }
        if (req.getWeight() == null || req.getWeight().signum() <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "weight는 0보다 커야 합니다.");
        }
        if (req.getBodyFatRate() != null) {
            double bfr = req.getBodyFatRate().doubleValue();
            if (bfr < 0 || bfr > 100) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "body_fat_rate는 0~100 사이여야 합니다.");
            }
        }
        if (req.getActivityType() != null &&
                !java.util.Set.of("SITTING", "STANDING", "PHYSICAL").contains(req.getActivityType())) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "activity_type 허용값: SITTING, STANDING, PHYSICAL");
        }
        if (req.getExerciseIntensity() != null &&
                !java.util.Set.of("LOW", "MEDIUM", "HIGH").contains(req.getExerciseIntensity())) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "exercise_intensity 허용값: LOW, MEDIUM, HIGH");
        }
    }
}