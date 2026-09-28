package com.example.nutriuniv.common.config;

import com.example.nutriuniv.common.security.JwtAuthenticationFilter;
import com.example.nutriuniv.common.security.JwtService;
import com.example.nutriuniv.common.security.SecurityErrorHandlers;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityErrorHandlers securityErrorHandlers;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401·403 을 컨트롤러 에러와 같은 CommonResponse.fail(ErrorResponse) 형태로 (SecurityErrorHandlers 참조)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandlers)
                        .accessDeniedHandler(securityErrorHandlers)
                )
                .authorizeHttpRequests(auth -> auth
                        // public
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/categories/**", "/brands").permitAll()
                        .requestMatchers("/logging/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        // 로그인·비로그인이 같은 API 를 쓰는 구간(기능명세서 공통 규칙). 여기서는 통과시키고
                        // 소유자 판정(JWT → 서버 발급 익명 ID → 없으면 403 CONSENT_REQUIRED)은 서비스(OwnerResolver)가 한다.
                        .requestMatchers("/app/**", "/onboarding/**", "/me/**").permitAll()
                        .requestMatchers("/search/**", "/grades/**", "/meta/**", "/rankings/**", "/links/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/analysis-requests", "/support/inquiries", "/products/*/reports").permitAll()
                        // admin
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 나머지(/users/**, 그 외) 인증 필요
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}