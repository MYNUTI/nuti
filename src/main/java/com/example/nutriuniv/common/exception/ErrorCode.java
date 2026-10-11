package com.example.nutriuniv.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값 유효성 검사에 실패했습니다."),
    INVALID_QUERY_PARAM(HttpStatus.BAD_REQUEST, "유효하지 않은 쿼리 파라미터입니다."),
    BARCODE_CHECKSUM_INVALID(HttpStatus.BAD_REQUEST, "바코드 체크섬이 일치하지 않습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    // 아래 둘은 클라이언트가 화면을 분기하는 코드 — 동의 화면을 띄운 뒤 원래 동작을 이어간다 (기능명세서 1.2·1.3)
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "개인정보 수집·이용 동의가 필요합니다."),
    HEALTH_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "건강정보 수집·이용 동의가 필요합니다."),

    // 404 Not Found
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 사용자를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "제품을 찾을 수 없습니다."),
    // 매핑은 있지만 게이트(분석 완료 300건 + A·D 각 1건)를 못 넘은 분류 — 클라이언트가 「아직 열리지 않은 분류」 화면을 띄운다 (기능명세서 6.3)
    RANKING_NOT_OPEN(HttpStatus.NOT_FOUND, "아직 랭킹이 열리지 않은 분류입니다."),

    // 409 Conflict
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "데이터가 이미 존재합니다."),
    STATE_CONFLICT(HttpStatus.CONFLICT, "리소스 상태가 충돌합니다."),
    POLICY_VERSION_CONFLICT(HttpStatus.CONFLICT, "처리방침 버전이 일치하지 않습니다. 다시 동의해 주세요."),
    GRADE_NOT_AVAILABLE(HttpStatus.CONFLICT, "영양정보가 부족해 등급을 제공할 수 없습니다."),
    ALREADY_ANALYZED(HttpStatus.CONFLICT, "이미 분석이 완료된 제품입니다."),
    SAVE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "저장 가능한 제품 수(200개)를 초과했습니다."),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않는 HTTP 메서드입니다."),

    // 415 Unsupported Media Type
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),

    // 422 Unprocessable Entity
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "요청은 올바르나 처리할 수 없습니다."),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수가 허용치를 초과했습니다."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "일일 요청 한도를 초과했습니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
