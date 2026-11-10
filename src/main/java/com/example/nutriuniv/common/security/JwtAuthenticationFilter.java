package com.example.nutriuniv.common.security;

import com.example.nutriuniv.domain.user.entity.User;
import com.example.nutriuniv.domain.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtService.isAccessToken(token)) {
                    Long userId = jwtService.getUserId(token);
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && user.isActive()) {
                        UserPrincipal principal = new UserPrincipal(user);
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (ExpiredJwtException e) {
                // 만료된 토큰 - SecurityContext 비워두고 통과 (401은 Security가 처리)
            }
        }

        filterChain.doFilter(request, response);
    }
}