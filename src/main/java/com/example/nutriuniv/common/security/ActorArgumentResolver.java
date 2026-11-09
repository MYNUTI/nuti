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

import java.util.regex.Pattern;

/**
 * 컨트롤러 메서드의 {@link Actor} 파라미터를 채운다.
 *
 * <p>로그인 여부는 JwtAuthenticationFilter 가 SecurityContext 에 넣어둔 UserPrincipal 로,
 * 익명 ID·세션·코호트는 클라이언트 헤더로, IP 는 X-Forwarded-For(프록시) 우선으로 해석한다.
 * 헤더 파싱·IP 해석 로직은 LoggingController.resolveContext 에 있던 것을 그대로 옮긴 것이다
 * (로깅 5종의 동작이 바뀌지 않도록). 이제 저장·목표·기여 등 개인 귀속 API 가 같은 해석을 공유한다.
 *
 * <p>익명 ID 는 기기가 처음 열릴 때 스스로 만든 UUID 다(v3 ACCT_001 — 서버가 발급하지 않고, 동의 없이 쓴다).
 * UUID 모양이 아닌 값은 없는 것으로 본다 — 로그·소유자에 아무 문자열이나 쌓이지 않게.
 */
@Component
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_ANONYMOUS_ID = "X-Anonymous-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";
    public static final String HEADER_COHORT = "X-Cohort";

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
                anonymousIdOrNull(request.getHeader(HEADER_ANONYMOUS_ID)),
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

    /** 기기 익명 ID — UUID(8-4-4-4-12 hex) 만 받고 소문자로 맞춘다. 그 외는 null. */
    static String anonymousIdOrNull(String value) {
        String v = trimToNull(value);
        if (v == null || !UUID_PATTERN.matcher(v).matches()) {
            return null;
        }
        return v.toLowerCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
