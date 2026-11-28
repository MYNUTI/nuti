package com.example.nutriuniv.domain.admin.service;

import com.example.nutriuniv.common.security.UserPrincipal;
import com.example.nutriuniv.domain.admin.entity.AdminActionLog;
import com.example.nutriuniv.domain.admin.repository.AdminActionLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 운영 처리 이력 남기기 (v3 ADM_007) — /admin/** 의 바꾸는 호출(POST · PUT · PATCH · DELETE)이 끝나면 한 줄.
 * 이력 저장이 실패해도 처리 결과는 막지 않는다(로그만).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionLogInterceptor implements HandlerInterceptor {

    private final AdminActionLogRepository repository;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) return;
        try {
            int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
            repository.save(AdminActionLog.of(adminUserId(), method, request.getRequestURI(), target(request), status));
        } catch (Exception e) {
            log.warn("[ADMIN] 처리 이력 저장 실패 {} {} — {}", method, request.getRequestURI(), e.getMessage());
        }
    }

    private static Long adminUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p.getId() : null;
    }

    @SuppressWarnings("unchecked")
    private static String target(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(vars instanceof Map<?, ?> m) || m.isEmpty()) return null;
        return ((Map<String, String>) m).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
