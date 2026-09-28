package com.example.nutriuniv.common.security;

import com.example.nutriuniv.common.exception.ErrorCode;
import com.example.nutriuniv.common.response.CommonResponse;
import com.example.nutriuniv.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터 단계에서 나는 401/403을 컨트롤러 에러와 같은 형태로 내려준다.
 *
 * <p>컨트롤러 예외는 GlobalExceptionHandler가 {@code {"isSuccess":false,"data":{code,message,path,...}}} 로
 * 내리는데, 필터에서 끊기는 401은 이전에 {@code {"success":false,"code":...}} 로 키 이름부터 달랐고
 * 403(admin 접근 거부)은 핸들러가 없어 Spring 기본 응답이 나갔다. 클라이언트가 에러 처리를 한 곳에서
 * 하도록 세 경우 모두 CommonResponse.fail(ErrorResponse) 한 형태로 맞춘다.
 * 직렬화는 Spring이 만든 ObjectMapper(컨트롤러와 동일 설정)를 그대로 써서 날짜 포맷도 같다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    // 인증 없음 / 토큰 무효·만료 → 401
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, ErrorCode.UNAUTHORIZED);
    }

    // 인증은 됐지만 권한 부족(예: 일반 유저의 /admin/**) → 403
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        CommonResponse<ErrorResponse> body =
                CommonResponse.fail(ErrorResponse.of(code, code.getMessage(), request.getRequestURI()));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
