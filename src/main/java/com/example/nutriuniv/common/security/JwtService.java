package com.example.nutriuniv.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtService {

    private final Key key;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessExpMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpMs) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessExpMs = accessExpMs;
        this.refreshExpMs = refreshExpMs;
    }

    public String generateAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpMs))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpMs))
                .signWith(key)
                .compact();
    }

    // ── 가입 토큰 (ACCT_004 가입 2단계) ──────────────────────────────────────────────

    public static final String TYPE_CLAIM = "typ";
    public static final String SIGNUP_TYPE = "signup";
    public static final long SIGNUP_EXP_MS = 30 * 60 * 1000L;   // 30분 — 수명은 명세 결정 대기

    /** 처음 로그인한 회사 계정 — 가입을 마칠 때까지 서버에 저장하지 않고 이 토큰에만 담는다. */
    public record SignupClaims(String provider, String oauthId, String email, String nickname) {}

    public String generateSignupToken(SignupClaims c) {
        return Jwts.builder()
                .subject(c.provider() + ":" + c.oauthId())
                .claim(TYPE_CLAIM, SIGNUP_TYPE)
                .claim("provider", c.provider())
                .claim("oauthId", c.oauthId())
                .claim("email", c.email())
                .claim("nickname", c.nickname())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SIGNUP_EXP_MS))
                .signWith(key)
                .compact();
    }

    /** 만료 · 위조 · 다른 종류의 토큰이면 null. */
    public SignupClaims parseSignupToken(String token) {
        try {
            Claims c = parseToken(token);
            if (!SIGNUP_TYPE.equals(c.get(TYPE_CLAIM, String.class))) return null;
            return new SignupClaims(c.get("provider", String.class), c.get("oauthId", String.class),
                    c.get("email", String.class), c.get("nickname", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 요청 인증에 쓸 수 있는 토큰 — 가입 토큰은 아니다. */
    public boolean isAccessToken(String token) {
        try {
            return !SIGNUP_TYPE.equals(parseToken(token).get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public boolean isExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getRefreshExpMs() { return refreshExpMs; }
}