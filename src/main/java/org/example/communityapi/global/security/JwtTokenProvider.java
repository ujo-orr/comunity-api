package org.example.communityapi.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // 1. 토큰 생성 (email 과 role 을 함께 담아 생성)
    public String createToken(String email, Object role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + expirationMs);

        // role이 Enum 타입(예: Role.ADMIN)일 수 있으므로 String으로 변환 처리
        String roleString = (role instanceof Enum) ? ((Enum<?>) role).name() : String.valueOf(role);

        return Jwts.builder()
                .subject(email)
                .claim("role", roleString) // 👈 Claims에 권한 정보 추가!
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    // 기존 단일 매개변수 createToken(email)도 필요한 경우를 위해 오버로딩 유지 가능
    public String createToken(String email) {
        return createToken(email, "USER");
    }

    // 2. 토큰에서 이메일(Subject) 추출
    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    // 3. 토큰에서 권한(Role) 추출 메서드 👈 [새로 추가]
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    // 4. 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 공통 Claims 파싱 내부 메서드
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
