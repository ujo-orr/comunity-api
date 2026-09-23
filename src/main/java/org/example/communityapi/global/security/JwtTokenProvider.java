package org.example.communityapi.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.example.communityapi.member.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    private String createToken(String email, String role, Long memberId, int tokenVersion, long expirationMs) {
        Instant now = Instant.now();
        Instant validity = now.plusMillis(expirationMs);

        // 동시에 발급한 토큰 구분용 ID 추가
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity));

        if (role != null) {
            builder.claim("role", role);
            builder.claim("memberId", memberId);
            builder.claim("tokenVersion", tokenVersion);
        }

        return builder.signWith(key).compact();
    }

    public String createAccessToken(String email, Role role, Long memberId, int tokenVersion) {
        return createToken(email, role.name(), memberId, tokenVersion, accessTokenExpirationMs);
    }

    public String createRefreshToken(String email) {
        return createToken(email, null, null, 0, refreshTokenExpirationMs);
    }

    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public long getExpiration(String token) {
        Date expiration = getClaimsFromToken(token).getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remainingTime, 0);
    }

    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
