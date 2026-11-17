package com.example.nutriuniv.domain.publicdata.client;

import java.util.List;
import java.util.Map;

/**
 * API 한 번 호출 결과 (sync.md 「완료 확인과 오류 처리」).
 * @param totalCount 전체 행 수(응답에 있으면). C005 는 믿을 수 없다(PublicApi.totalUntrusted)
 * @param rows       응답 행 — 필드는 응답 그대로(숫자처럼 보여도 문자열이면 문자열)
 */
public record ApiPage(Status status, String code, String message, Long totalCount, List<Map<String, Object>> rows) {

    public enum Status {
        /** 정상 (INFO-000 · 00) */
        OK,
        /** 진짜 0건 (INFO-200 · 03) */
        NO_DATA,
        /** 한도 초과 (INFO-300 · ERROR-503 · INFO-320 · 22) — 그 API 이번 회차는 멈추고 다음 날 이어 받는다 */
        QUOTA_EXCEEDED,
        /** 잠깐 뒤에 다시 (ERROR-500 · INFO-700 · HTTP 5xx · 시간 초과) */
        RETRY_LATER,
        /** 키 거부 — HTTP 200 에 JSON 대신 HTML(alert) 이 온다. 미신청 API 도 이렇게 온다 */
        KEY_REJECTED,
        /** 그 밖의 오류 (조건 이름 · 범위 오류 등) */
        ERROR
    }

    public static ApiPage of(Status status, String code, String message) {
        return new ApiPage(status, code, message, null, List.of());
    }

    public boolean ok() {
        return status == Status.OK;
    }
}