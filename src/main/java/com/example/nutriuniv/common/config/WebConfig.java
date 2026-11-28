package com.example.nutriuniv.common.config;

import com.example.nutriuniv.common.security.ActorArgumentResolver;
import com.example.nutriuniv.domain.admin.service.AdminActionLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ActorArgumentResolver actorArgumentResolver;
    private final AdminActionLogInterceptor adminActionLogInterceptor;

    // 운영 처리 이력 (ADM_007) — /admin/** 의 바꾸는 호출마다 누가 · 언제 · 무엇을
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminActionLogInterceptor).addPathPatterns("/admin/**");
    }

    // 컨트롤러 파라미터 Actor 자동 주입 (로그인/기기 ID/헤더 없음 구분)
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(actorArgumentResolver);
    }
}
