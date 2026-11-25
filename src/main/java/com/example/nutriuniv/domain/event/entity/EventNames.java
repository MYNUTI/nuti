package com.example.nutriuniv.domain.event.entity;

import java.util.Set;

/**
 * 받는 이벤트 이름 (v3 이벤트 목록 LOG_002, 63개). 목록에 없는 이름은 버린다.
 * 알림(11-24): analysis_notification_open = 분석 완료 알림을 눌러 들어옴(예전 이름 notification_open),
 * notification_menu_tap = 내 정보 「알림」 줄 누름(props.logged_in), notification_login_tap = 알림 줄 띠의 [로그인] 누름.
 * 띠에서 시작한 로그인은 login_complete 의 props.entry = "notification_prompt" 로 가른다(props 는 서버가 그대로 저장).
 */
public final class EventNames {

    private EventNames() {}

    public static final Set<String> KNOWN = Set.of(
            // 첫 방문 · 온보딩
            "first_visit", "app_return_open", "splash_shown", "onboarding_start", "onboarding_step", "goal_set",
            "onboarding_complete", "onboarding_abandon", "permission_denied", "onboarding_fallback",
            // 스캔
            "scan_path", "scan_complete", "aha_second_scan", "session_scan_count", "scan_context",
            "barcode_candidate_answer", "barcode_multi_pick", "scan_recognize_fail", "scan_not_found", "scan_checksum_fail",
            // 결과
            "result_view", "goal_preview_tap", "grade_criteria_view", "result_share", "purchase_link_tap",
            // 검색 · 랭킹
            "search_use", "search_autocomplete_tap", "search_result_tap", "search_zero_result", "zero_result_next_action",
            "ranking_view", "filter",
            // 비교 · 대안
            "compare_add", "compare_bar_view", "compare_view", "compare_tray_expired", "alternative_tap",
            // 저장 · 기록
            "product_save", "saved_list_view", "saved_product_tap", "history_view", "summary_view",
            // PWA
            "pwa_prompt_view", "pwa_prompt_action",
            // 기여
            "contribute_submit", "contribute_complete", "contribute_abandon", "contribution_list_view", "report_submit",
            // 계정 · 동의
            "login_complete", "signup_consent", "record_merge", "consent_change", "logout", "account_delete",
            "device_id_copy",
            // 알림 (스토어 앱 전에는 알림을 보내지 않고 누른 수만 센다 · RTN_008)
            "analysis_notification_open", "notification_menu_tap", "notification_login_tap",
            // 몸 정보
            "body_profile_consent", "body_profile_set", "body_profile_clear",
            // 세션
            "session_end");
}
