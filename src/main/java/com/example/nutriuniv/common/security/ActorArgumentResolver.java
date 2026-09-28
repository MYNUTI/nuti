package com.example.nutriuniv.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 메서드의 {@link Actor} 파라미터를 채운다.
 *
 * <p>로그인 여부는 JwtAuthenticationFilter 가 SecurityContext 에 넣어둔 UserPrincipal 로,
 * 익명 ID·세션·코호트는 클라이언트 헤더로, IP 는 X-Forwarded-For(프록시) 우선으로 해석한다.
 * 헤더 파싱·IP 해석 로직은 LoggingController.resolveContext 에 있던 것을 그대로 옮긴 것이다
 * (로깅 5종의 동작이 바뀌지 않도록). 이제 저장·목표·기여 등 개인 귀속 API 가 같은 해석을 공유한다.
 *
 * <p>익명 ID 는 여기서 존재 검증을 하지 않는다 — 로그는 어떤 값이든 기록해도 무방하고,
 * 소유권이 걸리는 API 는 서비스 단에서 발급된 ID 인지 확인한다.
 */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_ANONYMOUS_ID = "X-Anonymous-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";
    public static final String HEADER_COHORT = "X-Cohort";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        Long userId = null;
        String role = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            userId = principal.getId();
            role = principal.getRole();
        }

        return new Actor(
                userId,
                role,
                trimToNull(request.getHeader(HEADER_ANONYMOUS_ID)),
                trimToNull(request.getHeader(HEADER_SESSION_ID)),
                Actor.normalizeCohort(request.getHeader(HEADER_COHORT)),
                resolveClientIp(request)
        );
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            return request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
