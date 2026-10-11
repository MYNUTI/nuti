package com.example.nutriuniv.domain.logging.dto;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

/** POST /logging/onboarding-step 입력. 허용값 외는 400. */
@Getter
@NoArgsConstructor
public class OnboardingStepLogRequest {

    public static final Set<String> STEPS   = Set.of("CONSENT", "CAMERA", "START", "PICKER", "SCAN", "FIRST_RESULT", "GOAL", "DONE");
    public static final Set<String> ACTIONS = Set.of("VIEW", "ACCEPT", "SKIP");
    public static final Set<String> PICKER_REASONS = Set.of("NO_ITEM", "SCAN_FAIL", "NOT_IN_DATA", "PERMISSION_DENIED", "PC");

    private String step;
    private String action;
    private String reason;      // PICKER 진입 이유 5종 (선택)

    public String normalizedStep() {
        String s = up(step);
        if (s == null || !STEPS.contains(s)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "step은 " + String.join("·", STEPS) + " 중 하나여야 합니다.");
        }
        return s;
    }

    public String normalizedAction() {
        String a = up(action);
        if (a == null || !ACTIONS.contains(a)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "action은 VIEW·ACCEPT·SKIP 중 하나여야 합니다.");
        }
        return a;
    }

    /** reason 이 있으면 5종 중 하나여야 한다. 없으면 null. */
    public String normalizedReason() {
        String r = up(reason);
        if (r == null) return null;
        if (!PICKER_REASONS.contains(r)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "reason은 " + String.join("·", PICKER_REASONS) + " 중 하나여야 합니다.");
        }
        return r;
    }

    private static String up(String v) {
        return v == null || v.isBlank() ? null : v.trim().toUpperCase();
    }
}
