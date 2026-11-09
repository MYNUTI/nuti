package com.example.nutriuniv.common.exception;

import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // CustomException (비즈니스 예외)
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonResponse<ErrorResponse>> handleCustomException(
            CustomException e, HttpServletRequest request) {

        ErrorCode errorCode = e.getErrorCode();
        log.warn("[CustomException] url: {} | errorType: {} | message: {}",
                request.getRequestURI(), errorCode.name(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, e.getMessage(), request.getRequestURI())));
    }

    // 400 검증 에러
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<ErrorResponse>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        log.info("[ValidationException] url: {} | message: {}", request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, e.getBindingResult(), request.getRequestURI())));
    }

    // 405 Method Not Allowed
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<ErrorResponse>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        log.info("[MethodNotAllowed] url: {} | message: {}", request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, e.getMessage(), request.getRequestURI())));
    }

    // 415 Unsupported Media Type
    // POST /admin/products 엑셀 업로드 시 xlsx 가 아닌 파일을 올릴 때 발생
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CommonResponse<ErrorResponse>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        log.info("[UnsupportedMediaType] url: {} | message: {}", request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, e.getMessage(), request.getRequestURI())));
    }

    // 404 없는 주소 — 예전엔 아래 Exception 으로 떨어져 500 이 나갔다
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<ErrorResponse>> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        log.info("[NotFound] url: {}", request.getRequestURI());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI())));
    }

    // 400 본문·파라미터를 읽지 못함(깨진 JSON, 숫자 자리에 글자 등) — 예전엔 500
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResponse<ErrorResponse>> handleUnreadableRequest(
            Exception e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.BAD_REQUEST;
        log.info("[BadRequest] url: {} | message: {}", request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI())));
    }

    // 500 그 외 모든 예외 — 내부 예외 메시지(SQL·클래스명 등)는 로그에만 남기고 응답에는 싣지 않는다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<ErrorResponse>> handleAllException(
            Exception e, HttpServletRequest request) {

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.warn("[InternalServerError] url: {} | message: {}", request.getRequestURI(), e.getMessage(), e);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CommonResponse.fail(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI())));
    }
}